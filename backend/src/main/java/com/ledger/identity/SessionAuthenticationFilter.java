package com.ledger.identity;

import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationMembership;
import com.ledger.organization.OrganizationMembershipRepository;
import com.ledger.organization.OrganizationRepository;
import com.ledger.shared.LedgerPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final UserSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final String cookieName;

    public SessionAuthenticationFilter(
            UserSessionRepository sessionRepository,
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            @Value("${ledger.security.session-cookie-name:LEDGER_SESSION}") String cookieName) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String rawToken = extractCookie(request, cookieName);
        if (rawToken != null && !rawToken.isBlank()) {
            authenticateFromToken(rawToken, request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticateFromToken(String rawToken, HttpServletRequest request) {
        String hash = sha256Hex(rawToken);
        Optional<UserSession> optSession = sessionRepository.findByTokenHash(hash);
        if (optSession.isEmpty()) {
            return;
        }
        UserSession session = optSession.get();
        Instant now = Instant.now();
        if (!session.isValidAt(now)) {
            return;
        }
        Optional<User> optUser = userRepository.findById(session.getUserId());
        if (optUser.isEmpty() || !optUser.get().isActive()) {
            return;
        }
        User user = optUser.get();

        // Allow explicit X-Organization-Id override only when user has an active membership in that org.
        UUID targetOrgId = session.getActiveOrganizationId();
        String orgHeader = request.getHeader("X-Organization-Id");
        if (orgHeader != null && !orgHeader.isBlank()) {
            try {
                targetOrgId = UUID.fromString(orgHeader.trim());
            } catch (IllegalArgumentException ignored) {
                return;
            }
        }

        UUID resolvedOrgId = null;
        com.ledger.organization.OrgRole resolvedRole = null;
        boolean demoOrg = false;

        if (targetOrgId != null) {
            Optional<OrganizationMembership> membership =
                    membershipRepository.findByOrganizationIdAndUserIdAndActiveTrue(targetOrgId, user.getId());
            Optional<Organization> org = organizationRepository.findById(targetOrgId);
            if (membership.isPresent() && org.isPresent()) {
                // Enforce strict isolation: demo users can never access non-demo organizations.
                if (user.isDemoUser() && !org.get().isDemo()) {
                    return;
                }
                resolvedOrgId = org.get().getId();
                resolvedRole = membership.get().getRole();
                demoOrg = org.get().isDemo();
            } else if (orgHeader != null && !orgHeader.isBlank()) {
                // Reject unauthorized cross-organization header requests by leaving org context unset
                // so downstream handlers reject with 403/400 rather than falling back silently.
                resolvedOrgId = null;
            }
        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (resolvedRole != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + resolvedRole.name()));
        }

        LedgerPrincipal principal = new LedgerPrincipal(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.isDemoUser(),
                session.getId(),
                resolvedOrgId,
                resolvedRole,
                demoOrg
        );

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
