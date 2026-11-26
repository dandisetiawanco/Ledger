package com.ledger.identity;

import com.ledger.audit.AuditService;
import com.ledger.organization.OrgRole;
import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationMembership;
import com.ledger.organization.OrganizationMembershipRepository;
import com.ledger.organization.OrganizationRepository;
import com.ledger.organization.OrganizationService;
import com.ledger.shared.LedgerException;
import com.ledger.shared.LedgerPrincipal;
import com.ledger.shared.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationService organizationService;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter rateLimiter;
    private final AuditService auditService;
    private final String cookieName;
    private final boolean cookieSecure;
    private final long sessionTtlHours;

    public AuthService(
            UserRepository userRepository,
            UserSessionRepository sessionRepository,
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            OrganizationService organizationService,
            PasswordEncoder passwordEncoder,
            LoginRateLimiter rateLimiter,
            AuditService auditService,
            @Value("${ledger.security.session-cookie-name:LEDGER_SESSION}") String cookieName,
            @Value("${ledger.security.session-cookie-secure:false}") boolean cookieSecure,
            @Value("${ledger.security.session-ttl-hours:24}") long sessionTtlHours) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.organizationService = organizationService;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.sessionTtlHours = sessionTtlHours;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req, HttpServletRequest httpReq, HttpServletResponse httpRes) {
        String email = req.email().toLowerCase().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw LedgerException.conflict("EMAIL_EXISTS", "An account with this email already exists");
        }
        User user = new User(email, passwordEncoder.encode(req.password()), req.fullName(), false);
        userRepository.save(user);

        Organization org = null;
        if (req.organizationName() != null && !req.organizationName().isBlank()) {
            org = organizationService.createOrganizationInternal(
                    user,
                    req.organizationName().trim(),
                    req.businessType(),
                    req.baseCurrency() != null ? req.baseCurrency() : "IDR",
                    req.timeZone() != null ? req.timeZone() : "Asia/Jakarta",
                    req.locale() != null ? req.locale() : "id-ID",
                    req.fiscalYearStartMonth() != null ? req.fiscalYearStartMonth() : 1,
                    false
            );
        }

        UUID activeOrgId = org != null ? org.getId() : null;
        createSessionAndCookie(user, activeOrgId, httpReq, httpRes);
        auditService.recordWithActor(activeOrgId, user.getId(), user.getEmail(),
                "USER_REGISTERED", "USER", user.getId().toString(),
                "Registered new user account", Map.of("hasInitialOrganization", org != null));
        return buildAuthResponse(user, activeOrgId);
    }

    @Transactional
    public AuthResponse login(LoginRequest req, HttpServletRequest httpReq, HttpServletResponse httpRes) {
        String email = req.email().toLowerCase().trim();
        String clientIp = extractIp(httpReq);
        rateLimiter.checkAllowed(clientIp + ":" + email);

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> LedgerException.unauthorized("Invalid email or password"));
        if (!user.isActive() || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw LedgerException.unauthorized("Invalid email or password");
        }
        rateLimiter.clear(clientIp + ":" + email);

        List<OrganizationMembership> memberships = membershipRepository.findByUserIdAndActiveTrue(user.getId());
        UUID activeOrgId = memberships.isEmpty() ? null : memberships.getFirst().getOrganizationId();

        // Always issue a fresh random session token to prevent session fixation.
        createSessionAndCookie(user, activeOrgId, httpReq, httpRes);
        auditService.recordWithActor(activeOrgId, user.getId(), user.getEmail(),
                "USER_LOGIN", "SESSION", user.getId().toString(),
                "User signed in", Map.of("ip", clientIp));
        return buildAuthResponse(user, activeOrgId);
    }

    @Transactional
    public AuthResponse startSessionForUser(User user, UUID activeOrgId, HttpServletRequest httpReq, HttpServletResponse httpRes) {
        createSessionAndCookie(user, activeOrgId, httpReq, httpRes);
        return buildAuthResponse(user, activeOrgId);
    }

    @Transactional
    public void logout(LedgerPrincipal principal, HttpServletResponse httpRes) {
        sessionRepository.findById(principal.sessionId()).ifPresent(s -> {
            s.revoke();
            sessionRepository.save(s);
        });
        clearCookie(httpRes);
        auditService.record(principal.organizationId(), "USER_LOGOUT", "SESSION",
                principal.sessionId().toString(), "User signed out", Map.of());
    }

    @Transactional(readOnly = true)
    public AuthResponse currentUser(LedgerPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> LedgerException.unauthorized("User not found"));
        return buildAuthResponse(user, principal.organizationId());
    }

    @Transactional
    public AuthResponse switchOrganization(LedgerPrincipal principal, UUID targetOrgId) {
        OrganizationMembership membership = membershipRepository
                .findByOrganizationIdAndUserIdAndActiveTrue(targetOrgId, principal.userId())
                .orElseThrow(() -> LedgerException.forbidden("You are not a member of the requested organization"));
        Organization org = organizationRepository.findById(targetOrgId)
                .orElseThrow(() -> LedgerException.notFound("Organization not found"));
        if (principal.demoUser() && !org.isDemo()) {
            throw LedgerException.forbidden("Demo users cannot switch to non-demo organizations");
        }
        UserSession session = sessionRepository.findById(principal.sessionId())
                .orElseThrow(() -> LedgerException.unauthorized("Session not found"));
        session.setActiveOrganizationId(membership.getOrganizationId());
        sessionRepository.save(session);

        User user = userRepository.findById(principal.userId()).orElseThrow();
        return buildAuthResponse(user, targetOrgId);
    }

    @Transactional(readOnly = true)
    public List<SessionDto> listSessions(LedgerPrincipal principal) {
        Instant now = Instant.now();
        return sessionRepository.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(principal.userId())
                .stream()
                .filter(s -> s.isValidAt(now))
                .map(s -> new SessionDto(
                        s.getId(),
                        s.getIpAddress(),
                        s.getUserAgent(),
                        s.getCreatedAt(),
                        s.getExpiresAt(),
                        s.getId().equals(principal.sessionId())
                ))
                .toList();
    }

    @Transactional
    public void revokeSession(LedgerPrincipal principal, UUID sessionId) {
        UserSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> LedgerException.notFound("Session not found"));
        if (!session.getUserId().equals(principal.userId())) {
            throw LedgerException.forbidden("Cannot revoke another user's session");
        }
        session.revoke();
        sessionRepository.save(session);
        auditService.record(principal.organizationId(), "SESSION_REVOKED", "SESSION",
                sessionId.toString(), "Revoked active session", Map.of());
    }

    @Transactional
    public AuthResponse updateProfile(LedgerPrincipal principal, UpdateProfileRequest req) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> LedgerException.unauthorized("User not found"));
        if (user.isDemoUser()) {
            throw LedgerException.forbidden("Demo account profile cannot be modified");
        }
        if (req.fullName() != null && !req.fullName().isBlank()) {
            user.setFullName(req.fullName());
        }
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            if (req.currentPassword() == null || !passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
                throw LedgerException.badRequest("INVALID_PASSWORD", "Current password is incorrect");
            }
            if (req.newPassword().length() < 8) {
                throw LedgerException.badRequest("WEAK_PASSWORD", "New password must be at least 8 characters");
            }
            user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        }
        userRepository.save(user);
        return buildAuthResponse(user, principal.organizationId());
    }

    private void createSessionAndCookie(User user, UUID activeOrgId, HttpServletRequest httpReq, HttpServletResponse httpRes) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = SessionAuthenticationFilter.sha256Hex(rawToken);
        Instant expiresAt = Instant.now().plus(Duration.ofHours(sessionTtlHours));

        String ua = httpReq.getHeader("User-Agent");
        if (ua != null && ua.length() > 500) {
            ua = ua.substring(0, 500);
        }
        UserSession session = new UserSession(tokenHash, user.getId(), activeOrgId, extractIp(httpReq), ua, expiresAt);
        sessionRepository.save(session);

        ResponseCookie cookie = ResponseCookie.from(cookieName, rawToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofHours(sessionTtlHours))
                .build();
        httpRes.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse httpRes) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        httpRes.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr() != null ? req.getRemoteAddr() : "unknown";
    }

    public AuthResponse buildAuthResponse(User user, UUID activeOrgId) {
        List<OrganizationMembership> memberships = membershipRepository.findByUserIdAndActiveTrue(user.getId());
        List<OrgMembershipSummary> orgs = memberships.stream()
                .map(m -> organizationRepository.findById(m.getOrganizationId())
                        .map(o -> new OrgMembershipSummary(
                                o.getId(),
                                o.getName(),
                                o.getSlug(),
                                o.getBaseCurrency(),
                                o.getTimeZone(),
                                o.getLocale(),
                                m.getRole(),
                                o.isDemo(),
                                o.isOnboardingCompleted()
                        ))
                        .orElse(null))
                .filter(o -> o != null)
                .toList();

        OrgMembershipSummary activeOrg = orgs.stream()
                .filter(o -> o.organizationId().equals(activeOrgId))
                .findFirst()
                .orElse(orgs.isEmpty() ? null : orgs.getFirst());

        return new AuthResponse(
                new UserSummary(user.getId(), user.getEmail(), user.getFullName(), user.isDemoUser()),
                activeOrg,
                orgs
        );
    }

    public record RegisterRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 8, max = 128) String password,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 2, max = 200) String fullName,
            String organizationName,
            String businessType,
            String baseCurrency,
            String timeZone,
            String locale,
            Integer fiscalYearStartMonth
    ) {}

    public record LoginRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email,
            @jakarta.validation.constraints.NotBlank String password
    ) {}

    public record UpdateProfileRequest(
            String fullName,
            String currentPassword,
            String newPassword
    ) {}

    public record UserSummary(UUID id, String email, String fullName, boolean demoUser) {}

    public record OrgMembershipSummary(
            UUID organizationId,
            String name,
            String slug,
            String baseCurrency,
            String timeZone,
            String locale,
            OrgRole role,
            boolean demo,
            boolean onboardingCompleted
    ) {}

    public record AuthResponse(
            UserSummary user,
            OrgMembershipSummary activeOrganization,
            List<OrgMembershipSummary> organizations
    ) {}

    public record SessionDto(
            UUID id,
            String ipAddress,
            String userAgent,
            Instant createdAt,
            Instant expiresAt,
            boolean current
    ) {}
}
