package com.ledger.organization;

import com.ledger.audit.AuditService;
import com.ledger.identity.User;
import com.ledger.identity.UserRepository;
import com.ledger.identity.UserSessionRepository;
import com.ledger.shared.Currency;
import com.ledger.shared.CurrencyRepository;
import com.ledger.shared.LedgerException;
import com.ledger.shared.LedgerPrincipal;
import com.ledger.shared.MoneyUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final FinancialSettingsRepository settingsRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final CurrencyRepository currencyRepository;
    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final List<OrganizationInitializer> initializers;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            FinancialSettingsRepository settingsRepository,
            ExchangeRateRepository exchangeRateRepository,
            CurrencyRepository currencyRepository,
            UserRepository userRepository,
            UserSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            List<OrganizationInitializer> initializers) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.settingsRepository = settingsRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.currencyRepository = currencyRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.initializers = initializers;
    }

    @Transactional
    public Organization createOrganizationInternal(
            User owner,
            String name,
            String businessType,
            String baseCurrency,
            String timeZone,
            String locale,
            int fiscalYearStartMonth,
            boolean demo
    ) {
        String currencyCode = baseCurrency != null ? baseCurrency.toUpperCase(Locale.ROOT) : "IDR";
        if (!currencyRepository.existsById(currencyCode)) {
            throw LedgerException.badRequest("INVALID_CURRENCY", "Unsupported currency code: " + currencyCode);
        }
        String slug = generateUniqueSlug(name);
        Organization org = new Organization(
                name.trim(),
                slug,
                businessType != null ? businessType.trim() : "Professional Services",
                currencyCode,
                timeZone != null && !timeZone.isBlank() ? timeZone.trim() : "Asia/Jakarta",
                locale != null && !locale.isBlank() ? locale.trim() : "id-ID",
                Math.min(Math.max(fiscalYearStartMonth, 1), 12),
                demo
        );
        organizationRepository.save(org);

        OrganizationMembership membership = new OrganizationMembership(org.getId(), owner.getId(), OrgRole.OWNER);
        membershipRepository.save(membership);

        FinancialSettings settings = new FinancialSettings(org.getId());
        settingsRepository.save(settings);

        for (OrganizationInitializer initializer : initializers) {
            initializer.initializeOrganization(org, owner.getId());
        }

        auditService.recordWithActor(org.getId(), owner.getId(), owner.getEmail(),
                "ORGANIZATION_CREATED", "ORGANIZATION", org.getId().toString(),
                "Created organization " + org.getName(),
                Map.of("baseCurrency", org.getBaseCurrency(), "demo", demo));
        return org;
    }

    @Transactional
    public OrganizationDetailDto createOrganization(LedgerPrincipal principal, CreateOrganizationRequest req) {
        if (principal.demoUser()) {
            throw LedgerException.forbidden("Demo users cannot create new organizations");
        }
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> LedgerException.unauthorized("User not found"));
        Organization org = createOrganizationInternal(
                user,
                req.name(),
                req.businessType(),
                req.baseCurrency() != null ? req.baseCurrency() : "IDR",
                req.timeZone() != null ? req.timeZone() : "Asia/Jakarta",
                req.locale() != null ? req.locale() : "id-ID",
                req.fiscalYearStartMonth() != null ? req.fiscalYearStartMonth() : 1,
                false
        );
        if (req.contactEmail() != null) org.setContactEmail(req.contactEmail());
        if (req.contactPhone() != null) org.setContactPhone(req.contactPhone());
        if (req.address() != null) org.setAddress(req.address());
        if (req.taxId() != null) org.setTaxId(req.taxId());
        if (req.onboardingCompleted() != null) org.setOnboardingCompleted(req.onboardingCompleted());
        organizationRepository.save(org);

        FinancialSettings settings = settingsRepository.findByOrganizationId(org.getId()).orElseThrow();
        applySettingsUpdates(settings, req.invoicePrefix(), req.defaultTaxName(), req.defaultTaxRate(),
                req.taxInclusiveDefault(), req.defaultPaymentTermsDays(), req.paymentInstructions(), req.invoiceFooterNotes());
        settingsRepository.save(settings);

        sessionRepository.findById(principal.sessionId()).ifPresent(s -> {
            s.setActiveOrganizationId(org.getId());
            sessionRepository.save(s);
        });

        return toDetailDto(org, settings);
    }

    @Transactional(readOnly = true)
    public OrganizationDetailDto getCurrentOrganization(UUID orgId) {
        Organization org = getOrganizationEntity(orgId);
        FinancialSettings settings = settingsRepository.findByOrganizationId(orgId)
                .orElseThrow(() -> LedgerException.notFound("Financial settings not found"));
        return toDetailDto(org, settings);
    }

    @Transactional(readOnly = true)
    public Organization getOrganizationEntity(UUID orgId) {
        return organizationRepository.findById(orgId)
                .orElseThrow(() -> LedgerException.notFound("Organization not found"));
    }

    @Transactional(readOnly = true)
    public FinancialSettings getSettingsEntity(UUID orgId) {
        return settingsRepository.findByOrganizationId(orgId)
                .orElseThrow(() -> LedgerException.notFound("Financial settings not found"));
    }

    @Transactional
    public OrganizationDetailDto updateOrganization(LedgerPrincipal principal, UpdateOrganizationRequest req) {
        principal.requireOrgAdmin();
        UUID orgId = principal.requireOrganizationId();
        Organization org = getOrganizationEntity(orgId);
        FinancialSettings settings = getSettingsEntity(orgId);

        if (req.name() != null && !req.name().isBlank()) org.setName(req.name().trim());
        if (req.businessType() != null) org.setBusinessType(req.businessType().trim());
        if (req.timeZone() != null && !req.timeZone().isBlank()) org.setTimeZone(req.timeZone().trim());
        if (req.locale() != null && !req.locale().isBlank()) org.setLocale(req.locale().trim());
        if (req.contactEmail() != null) org.setContactEmail(req.contactEmail().trim());
        if (req.contactPhone() != null) org.setContactPhone(req.contactPhone().trim());
        if (req.address() != null) org.setAddress(req.address().trim());
        if (req.taxId() != null) org.setTaxId(req.taxId().trim());
        if (req.onboardingCompleted() != null) org.setOnboardingCompleted(req.onboardingCompleted());
        organizationRepository.save(org);

        applySettingsUpdates(settings, req.invoicePrefix(), req.defaultTaxName(), req.defaultTaxRate(),
                req.taxInclusiveDefault(), req.defaultPaymentTermsDays(), req.paymentInstructions(), req.invoiceFooterNotes());
        if (req.requireExpenseApproval() != null) settings.setRequireExpenseApproval(req.requireExpenseApproval());
        if (req.requireJournalApproval() != null) settings.setRequireJournalApproval(req.requireJournalApproval());
        if (req.requireInvoiceApproval() != null) settings.setRequireInvoiceApproval(req.requireInvoiceApproval());
        if (req.enforceSeparationOfDuties() != null) settings.setEnforceSeparationOfDuties(req.enforceSeparationOfDuties());
        if (req.expenseApprovalThreshold() != null) settings.setExpenseApprovalThreshold(MoneyUtil.normalize(req.expenseApprovalThreshold()));
        settingsRepository.save(settings);

        auditService.record(orgId, "ORGANIZATION_UPDATED", "ORGANIZATION", orgId.toString(),
                "Updated organization and financial settings", Map.of("name", org.getName()));
        return toDetailDto(org, settings);
    }

    @Transactional(readOnly = true)
    public List<MemberDto> listMembers(UUID orgId) {
        return membershipRepository.findByOrganizationId(orgId).stream()
                .map(m -> userRepository.findById(m.getUserId())
                        .map(u -> new MemberDto(m.getId(), u.getId(), u.getEmail(), u.getFullName(), m.getRole(), m.isActive(), m.getCreatedAt()))
                        .orElse(null))
                .filter(dto -> dto != null)
                .toList();
    }

    @Transactional
    public MemberDto addOrInviteMember(LedgerPrincipal principal, AddMemberRequest req) {
        principal.requireOrgAdmin();
        UUID orgId = principal.requireOrganizationId();
        Organization org = getOrganizationEntity(orgId);
        if (org.isDemo() && principal.demoUser()) {
            throw LedgerException.forbidden("Team membership cannot be altered in public demonstration mode");
        }

        String email = req.email().toLowerCase().trim();
        User targetUser = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            String initialPass = (req.temporaryPassword() != null && req.temporaryPassword().length() >= 8)
                    ? req.temporaryPassword()
                    : UUID.randomUUID().toString();
            String fullName = (req.fullName() != null && !req.fullName().isBlank()) ? req.fullName().trim() : email.split("@")[0];
            User created = new User(email, passwordEncoder.encode(initialPass), fullName, false);
            return userRepository.save(created);
        });

        OrganizationMembership membership = membershipRepository
                .findByOrganizationIdAndUserId(orgId, targetUser.getId())
                .map(existing -> {
                    existing.setRole(req.role());
                    existing.setActive(true);
                    return existing;
                })
                .orElseGet(() -> new OrganizationMembership(orgId, targetUser.getId(), req.role()));
        membershipRepository.save(membership);

        auditService.record(orgId, "MEMBER_ADDED", "MEMBERSHIP", membership.getId().toString(),
                "Added " + targetUser.getEmail() + " with role " + req.role(),
                Map.of("targetUserId", targetUser.getId().toString(), "role", req.role().name()));

        return new MemberDto(membership.getId(), targetUser.getId(), targetUser.getEmail(),
                targetUser.getFullName(), membership.getRole(), membership.isActive(), membership.getCreatedAt());
    }

    @Transactional
    public MemberDto updateMemberRole(LedgerPrincipal principal, UUID membershipId, UpdateMemberRoleRequest req) {
        principal.requireOrgAdmin();
        UUID orgId = principal.requireOrganizationId();
        OrganizationMembership membership = membershipRepository.findById(membershipId)
                .filter(m -> m.getOrganizationId().equals(orgId))
                .orElseThrow(() -> LedgerException.notFound("Membership not found"));

        if (membership.getRole() == OrgRole.OWNER && (req.role() != OrgRole.OWNER || Boolean.FALSE.equals(req.active()))) {
            long activeOwners = membershipRepository.countByOrganizationIdAndRoleAndActiveTrue(orgId, OrgRole.OWNER);
            if (activeOwners <= 1) {
                throw LedgerException.businessRule("LAST_OWNER", "Organization must retain at least one active Owner");
            }
        }
        OrgRole previous = membership.getRole();
        if (req.role() != null) membership.setRole(req.role());
        if (req.active() != null) membership.setActive(req.active());
        membershipRepository.save(membership);

        User u = userRepository.findById(membership.getUserId()).orElseThrow();
        auditService.record(orgId, "PERMISSION_CHANGED", "MEMBERSHIP", membership.getId().toString(),
                "Changed role for " + u.getEmail() + " from " + previous + " to " + membership.getRole(),
                Map.of("active", membership.isActive()));

        return new MemberDto(membership.getId(), u.getId(), u.getEmail(), u.getFullName(),
                membership.getRole(), membership.isActive(), membership.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<Currency> listCurrencies() {
        return currencyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ExchangeRateDto> listExchangeRates(UUID orgId) {
        return exchangeRateRepository.findByOrganizationIdOrderByEffectiveDateDesc(orgId).stream()
                .map(ExchangeRateDto::from)
                .toList();
    }

    @Transactional
    public ExchangeRateDto upsertExchangeRate(LedgerPrincipal principal, UpsertExchangeRateRequest req) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();
        String from = req.fromCurrency().toUpperCase(Locale.ROOT);
        String to = req.toCurrency().toUpperCase(Locale.ROOT);
        if (from.equals(to)) {
            throw LedgerException.badRequest("SAME_CURRENCY", "From and to currencies must differ");
        }
        if (!currencyRepository.existsById(from) || !currencyRepository.existsById(to)) {
            throw LedgerException.badRequest("INVALID_CURRENCY", "Unsupported currency code");
        }
        if (req.rate() == null || req.rate().compareTo(BigDecimal.ZERO) <= 0) {
            throw LedgerException.badRequest("INVALID_RATE", "Exchange rate must be greater than zero");
        }

        ExchangeRate er = exchangeRateRepository
                .findByOrganizationIdAndFromCurrencyAndToCurrencyAndEffectiveDate(orgId, from, to, req.effectiveDate())
                .map(existing -> {
                    existing.setRate(req.rate());
                    return existing;
                })
                .orElseGet(() -> new ExchangeRate(orgId, from, to, req.rate(), req.effectiveDate(), principal.userId()));
        exchangeRateRepository.save(er);

        auditService.record(orgId, "EXCHANGE_RATE_UPDATED", "EXCHANGE_RATE", er.getId().toString(),
                "Configured manual rate " + from + "/" + to + " = " + req.rate().toPlainString() + " effective " + req.effectiveDate(),
                Map.of("source", "MANUAL"));
        return ExchangeRateDto.from(er);
    }

    @Transactional(readOnly = true)
    public BigDecimal resolveExchangeRate(UUID orgId, String currencyCode, LocalDate asOfDate, BigDecimal explicitRate) {
        Organization org = getOrganizationEntity(orgId);
        String base = org.getBaseCurrency();
        String target = currencyCode != null ? currencyCode.toUpperCase(Locale.ROOT) : base;
        if (base.equals(target)) {
            return BigDecimal.ONE;
        }
        if (explicitRate != null && explicitRate.compareTo(BigDecimal.ZERO) > 0) {
            return explicitRate;
        }
        List<ExchangeRate> rates = exchangeRateRepository.findEffectiveRates(orgId, target, base, asOfDate);
        if (!rates.isEmpty()) {
            return rates.getFirst().getRate();
        }
        throw LedgerException.businessRule(
                "EXCHANGE_RATE_MISSING",
                "No exchange rate configured for " + target + " to " + base + " on or before " + asOfDate
        );
    }

    private void applySettingsUpdates(FinancialSettings settings, String invoicePrefix, String defaultTaxName,
                                      BigDecimal defaultTaxRate, Boolean taxInclusive, Integer paymentTermsDays,
                                      String paymentInstructions, String invoiceFooterNotes) {
        if (invoicePrefix != null && !invoicePrefix.isBlank()) settings.setInvoicePrefix(invoicePrefix.trim().toUpperCase(Locale.ROOT));
        if (defaultTaxName != null && !defaultTaxName.isBlank()) settings.setDefaultTaxName(defaultTaxName.trim());
        if (defaultTaxRate != null && defaultTaxRate.compareTo(BigDecimal.ZERO) >= 0) settings.setDefaultTaxRate(defaultTaxRate);
        if (taxInclusive != null) settings.setTaxInclusiveDefault(taxInclusive);
        if (paymentTermsDays != null && paymentTermsDays >= 0) settings.setDefaultPaymentTermsDays(paymentTermsDays);
        if (paymentInstructions != null) settings.setPaymentInstructions(paymentInstructions);
        if (invoiceFooterNotes != null) settings.setInvoiceFooterNotes(invoiceFooterNotes);
    }

    private String generateUniqueSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (base.isBlank()) base = "org";
        if (base.length() > 80) base = base.substring(0, 80);
        String candidate = base;
        int suffix = 1;
        while (organizationRepository.existsBySlug(candidate)) {
            candidate = base + "-" + (++suffix);
        }
        return candidate;
    }

    private OrganizationDetailDto toDetailDto(Organization o, FinancialSettings s) {
        return new OrganizationDetailDto(
                o.getId(), o.getName(), o.getSlug(), o.getBusinessType(), o.getBaseCurrency(),
                o.getTimeZone(), o.getLocale(), o.getFiscalYearStartMonth(),
                o.getContactEmail(), o.getContactPhone(), o.getAddress(), o.getTaxId(),
                o.getLogoAttachmentId(), o.isDemo(), o.isOnboardingCompleted(),
                new FinancialSettingsDto(
                        s.getInvoicePrefix(), s.getCreditNotePrefix(), s.getJournalPrefix(),
                        s.getPaymentPrefix(), s.getExpensePrefix(), s.getDefaultPaymentTermsDays(),
                        s.getDefaultTaxName(), s.getDefaultTaxRate().toPlainString(),
                        s.isTaxInclusiveDefault(), s.isRequireExpenseApproval(),
                        s.isRequireJournalApproval(), s.isRequireInvoiceApproval(),
                        s.isEnforceSeparationOfDuties(),
                        MoneyUtil.toMoneyString(s.getExpenseApprovalThreshold()),
                        s.getPaymentInstructions(), s.getInvoiceFooterNotes()
                )
        );
    }

    public record CreateOrganizationRequest(
            @jakarta.validation.constraints.NotBlank String name,
            String businessType,
            String baseCurrency,
            String timeZone,
            String locale,
            Integer fiscalYearStartMonth,
            String contactEmail,
            String contactPhone,
            String address,
            String taxId,
            String invoicePrefix,
            String defaultTaxName,
            BigDecimal defaultTaxRate,
            Boolean taxInclusiveDefault,
            Integer defaultPaymentTermsDays,
            String paymentInstructions,
            String invoiceFooterNotes,
            Boolean onboardingCompleted
    ) {}

    public record UpdateOrganizationRequest(
            String name,
            String businessType,
            String timeZone,
            String locale,
            String contactEmail,
            String contactPhone,
            String address,
            String taxId,
            Boolean onboardingCompleted,
            String invoicePrefix,
            String defaultTaxName,
            BigDecimal defaultTaxRate,
            Boolean taxInclusiveDefault,
            Integer defaultPaymentTermsDays,
            Boolean requireExpenseApproval,
            Boolean requireJournalApproval,
            Boolean requireInvoiceApproval,
            Boolean enforceSeparationOfDuties,
            BigDecimal expenseApprovalThreshold,
            String paymentInstructions,
            String invoiceFooterNotes
    ) {}

    public record AddMemberRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email,
            String fullName,
            String temporaryPassword,
            @jakarta.validation.constraints.NotNull OrgRole role
    ) {}

    public record UpdateMemberRoleRequest(OrgRole role, Boolean active) {}

    public record UpsertExchangeRateRequest(
            @jakarta.validation.constraints.NotBlank String fromCurrency,
            @jakarta.validation.constraints.NotBlank String toCurrency,
            @jakarta.validation.constraints.NotNull BigDecimal rate,
            @jakarta.validation.constraints.NotNull LocalDate effectiveDate
    ) {}

    public record FinancialSettingsDto(
            String invoicePrefix,
            String creditNotePrefix,
            String journalPrefix,
            String paymentPrefix,
            String expensePrefix,
            int defaultPaymentTermsDays,
            String defaultTaxName,
            String defaultTaxRate,
            boolean taxInclusiveDefault,
            boolean requireExpenseApproval,
            boolean requireJournalApproval,
            boolean requireInvoiceApproval,
            boolean enforceSeparationOfDuties,
            String expenseApprovalThreshold,
            String paymentInstructions,
            String invoiceFooterNotes
    ) {}

    public record OrganizationDetailDto(
            UUID id,
            String name,
            String slug,
            String businessType,
            String baseCurrency,
            String timeZone,
            String locale,
            int fiscalYearStartMonth,
            String contactEmail,
            String contactPhone,
            String address,
            String taxId,
            UUID logoAttachmentId,
            boolean demo,
            boolean onboardingCompleted,
            FinancialSettingsDto settings
    ) {}

    public record MemberDto(
            UUID membershipId,
            UUID userId,
            String email,
            String fullName,
            OrgRole role,
            boolean active,
            java.time.Instant joinedAt
    ) {}

    public record ExchangeRateDto(
            UUID id,
            String fromCurrency,
            String toCurrency,
            String rate,
            LocalDate effectiveDate,
            String source
    ) {
        static ExchangeRateDto from(ExchangeRate er) {
            return new ExchangeRateDto(
                    er.getId(), er.getFromCurrency(), er.getToCurrency(),
                    er.getRate().stripTrailingZeros().toPlainString(), er.getEffectiveDate(), er.getSource()
            );
        }
    }
}
