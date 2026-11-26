package com.ledger.journal;

import com.ledger.accounting.Account;
import com.ledger.accounting.AccountRepository;
import com.ledger.accounting.AccountService;
import com.ledger.accounting.AccountingPeriod;
import com.ledger.accounting.PeriodService;
import com.ledger.audit.AuditService;
import com.ledger.organization.FinancialSettings;
import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationService;
import com.ledger.shared.DocumentSequenceService;
import com.ledger.shared.DomainEvents;
import com.ledger.shared.LedgerException;
import com.ledger.shared.LedgerPrincipal;
import com.ledger.shared.MoneyUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Core double-entry accounting engine. Enforces balanced debit/credit totals,
 * posting account eligibility, open accounting periods, posted immutability,
 * duplicate event prevention, and linked reversal entries inside atomic transactions.
 */
@Service
public class JournalService {

    private final JournalEntryRepository entryRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final PeriodService periodService;
    private final OrganizationService organizationService;
    private final DocumentSequenceService sequenceService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public JournalService(
            JournalEntryRepository entryRepository,
            AccountRepository accountRepository,
            AccountService accountService,
            PeriodService periodService,
            OrganizationService organizationService,
            DocumentSequenceService sequenceService,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher) {
        this.entryRepository = entryRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.periodService = periodService;
        this.organizationService = organizationService;
        this.sequenceService = sequenceService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JournalEntryDto createManualJournal(LedgerPrincipal principal, CreateJournalRequest req) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();

        JournalSourceType sourceType = req.openingBalance() != null && req.openingBalance()
                ? JournalSourceType.OPENING_BALANCE
                : JournalSourceType.MANUAL;

        FinancialSettings settings = organizationService.getSettingsEntity(orgId);
        boolean postNow = Boolean.TRUE.equals(req.postImmediately());
        if (postNow && settings.isRequireJournalApproval()) {
            throw LedgerException.businessRule(
                    "JOURNAL_APPROVAL_REQUIRED",
                    "Manual journals require approval before posting under current organization settings"
            );
        }

        JournalEntry entry = buildAndSaveEntry(
                orgId,
                principal.userId(),
                req.entryDate(),
                req.description(),
                req.reference(),
                sourceType,
                null,
                null,
                req.currencyCode(),
                req.exchangeRate(),
                req.lines(),
                postNow
        );
        return toDto(entry);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JournalEntry postAutomatedJournal(
            UUID organizationId,
            UUID actorUserId,
            LocalDate entryDate,
            String description,
            String reference,
            JournalSourceType sourceType,
            UUID sourceId,
            String businessEventKey,
            String currencyCode,
            BigDecimal exchangeRate,
            List<JournalLineInput> lines
    ) {
        if (businessEventKey != null && !businessEventKey.isBlank()
                && entryRepository.existsByOrganizationIdAndBusinessEventKey(organizationId, businessEventKey)) {
            throw LedgerException.conflict("DUPLICATE_POSTING", "Accounting entry already posted for event: " + businessEventKey);
        }
        return buildAndSaveEntry(
                organizationId, actorUserId, entryDate, description, reference,
                sourceType, sourceId, businessEventKey, currencyCode, exchangeRate, lines, true
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JournalEntryDto updateDraftJournal(LedgerPrincipal principal, UUID journalId, UpdateJournalRequest req) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();
        JournalEntry entry = entryRepository.findByIdAndOrganizationId(journalId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Journal entry not found"));

        if (entry.getStatus() == JournalStatus.POSTED || entry.getStatus() == JournalStatus.REVERSED) {
            throw LedgerException.businessRule(
                    "JOURNAL_IMMUTABLE",
                    "Posted or reversed journal entries are immutable. Use a reversal entry to correct errors."
            );
        }

        Organization org = organizationService.getOrganizationEntity(orgId);
        LocalDate effectiveDate = req.entryDate() != null ? req.entryDate() : entry.getEntryDate();
        AccountingPeriod period = periodService.requireOpenPeriodForDate(orgId, effectiveDate);

        if (req.entryDate() != null) entry.setEntryDate(effectiveDate);
        entry.setAccountingPeriodId(period.getId());
        if (req.description() != null && !req.description().isBlank()) entry.setDescription(req.description().trim());
        if (req.reference() != null) entry.setReference(req.reference().trim());

        if (req.lines() != null) {
            entry.clearLines();
            BigDecimal[] totals = attachValidatedLines(entry, org, req.lines(), false);
            entry.setTotals(totals[0], totals[1]);
        }
        entryRepository.save(entry);
        return toDto(entry);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JournalEntryDto postJournal(LedgerPrincipal principal, UUID journalId) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();
        JournalEntry entry = entryRepository.findByIdAndOrganizationId(journalId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Journal entry not found"));

        if (entry.getStatus() == JournalStatus.POSTED) {
            throw LedgerException.businessRule("ALREADY_POSTED", "Journal entry is already posted");
        }
        if (entry.getStatus() == JournalStatus.REVERSED) {
            throw LedgerException.businessRule("JOURNAL_REVERSED", "Reversed journal entry cannot be posted");
        }
        if (entry.getStatus() == JournalStatus.PENDING_APPROVAL) {
            throw LedgerException.businessRule("APPROVAL_PENDING", "Journal entry is awaiting approval before posting");
        }
        FinancialSettings settings = organizationService.getSettingsEntity(orgId);
        if (entry.getSourceType() == JournalSourceType.MANUAL
                && settings.isRequireJournalApproval()
                && entry.getApprovedBy() == null) {
            throw LedgerException.businessRule("APPROVAL_REQUIRED", "Manual journal requires approval before posting");
        }

        AccountingPeriod period = periodService.requireOpenPeriodForDate(orgId, entry.getEntryDate());
        entry.setAccountingPeriodId(period.getId());
        validateBalancedLines(orgId, entry.getLines());

        entry.markPosted(principal.userId());
        entryRepository.save(entry);

        auditService.record(orgId, "JOURNAL_POSTED", "JOURNAL_ENTRY", entry.getId().toString(),
                "Posted journal " + entry.getEntryNumber() + " (" + MoneyUtil.toMoneyString(entry.getTotalDebit()) + ")",
                Map.of("sourceType", entry.getSourceType().name()));
        eventPublisher.publishEvent(new DomainEvents.JournalPostedEvent(
                orgId, entry.getId(), entry.getEntryNumber(), entry.getEntryDate(), entry.getTotalDebit()));
        return toDto(entry);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteDraftJournal(LedgerPrincipal principal, UUID journalId) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();
        JournalEntry entry = entryRepository.findByIdAndOrganizationId(journalId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Journal entry not found"));
        if (entry.getStatus() != JournalStatus.DRAFT) {
            throw LedgerException.businessRule("JOURNAL_IMMUTABLE", "Only draft journal entries can be deleted");
        }
        entryRepository.delete(entry);
        auditService.record(orgId, "JOURNAL_DRAFT_DELETED", "JOURNAL_ENTRY", journalId.toString(),
                "Deleted draft journal " + entry.getEntryNumber(), Map.of());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JournalEntryDto reverseJournal(LedgerPrincipal principal, UUID journalId, LocalDate reversalDate, String reason) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();
        JournalEntry reversed = reverseJournalInternal(orgId, principal.userId(), journalId, reversalDate, reason);
        return toDto(reversed);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JournalEntry reverseJournalInternal(UUID organizationId, UUID actorUserId, UUID journalId,
                                               LocalDate reversalDate, String reason) {
        JournalEntry original = entryRepository.findByIdAndOrganizationId(journalId, organizationId)
                .orElseThrow(() -> LedgerException.notFound("Journal entry not found"));

        if (original.getStatus() != JournalStatus.POSTED) {
            throw LedgerException.businessRule("ONLY_POSTED_REVERSIBLE", "Only posted journal entries can be reversed");
        }
        if (original.getReversalEntryId() != null) {
            throw LedgerException.conflict("ALREADY_REVERSED", "Journal entry has already been reversed");
        }

        LocalDate effDate = reversalDate != null ? reversalDate : LocalDate.now();
        AccountingPeriod period = periodService.requireOpenPeriodForDate(organizationId, effDate);
        FinancialSettings settings = organizationService.getSettingsEntity(organizationId);
        String entryNumber = sequenceService.nextNumber(organizationId, "JOURNAL", settings.getJournalPrefix(), effDate.getYear());

        String desc = "Reversal of " + original.getEntryNumber()
                + (reason != null && !reason.isBlank() ? " (" + reason.trim() + ")" : "");
        JournalEntry reversal = new JournalEntry(
                organizationId, entryNumber, effDate, period.getId(), desc,
                original.getReference(), JournalStatus.POSTED, JournalSourceType.REVERSAL,
                original.getId(), "REVERSAL:" + original.getId(),
                original.getCurrencyCode(), original.getExchangeRate(), original.getExchangeRateDate(), actorUserId
        );
        reversal.setReversedEntryId(original.getId());

        int lineNo = 1;
        for (JournalLine origLine : original.getLines()) {
            // Swap debit and credit exactly to preserve historical base amounts and zero out the original entry.
            JournalLine revLine = new JournalLine(
                    organizationId,
                    lineNo++,
                    origLine.getAccountId(),
                    origLine.getContactId(),
                    origLine.getDescription(),
                    origLine.getCurrencyCode(),
                    origLine.getOriginalCredit(),
                    origLine.getOriginalDebit(),
                    origLine.getCreditAmount(),
                    origLine.getDebitAmount()
            );
            reversal.addLine(revLine);
        }
        reversal.setTotals(original.getTotalCredit(), original.getTotalDebit());
        reversal.markPosted(actorUserId);
        entryRepository.save(reversal);

        original.markReversed(reversal.getId());
        entryRepository.save(original);

        auditService.record(organizationId, "JOURNAL_REVERSED", "JOURNAL_ENTRY", original.getId().toString(),
                "Reversed journal " + original.getEntryNumber() + " with " + reversal.getEntryNumber(),
                Map.of("reversalEntryId", reversal.getId().toString()));
        eventPublisher.publishEvent(new DomainEvents.JournalReversedEvent(
                organizationId, original.getId(), reversal.getId(), reversal.getEntryNumber()));
        return reversal;
    }

    @Transactional(readOnly = true)
    public Page<JournalEntryDto> search(UUID organizationId, JournalStatus status, JournalSourceType sourceType,
                                        LocalDate fromDate, LocalDate toDate, String search, Pageable pageable) {
        String cleanSearch = (search == null || search.isBlank()) ? null : search.trim();
        Page<JournalEntry> page = entryRepository.search(organizationId, status, sourceType, fromDate, toDate, cleanSearch, pageable);
        Map<UUID, Account> accountMap = loadAccountMap(organizationId);
        return page.map(e -> toDtoWithMap(e, accountMap));
    }

    @Transactional(readOnly = true)
    public JournalEntryDto getById(UUID organizationId, UUID journalId) {
        JournalEntry entry = entryRepository.findByIdAndOrganizationId(journalId, organizationId)
                .orElseThrow(() -> LedgerException.notFound("Journal entry not found"));
        return toDto(entry);
    }

    @Transactional
    public void markPendingApproval(UUID organizationId, UUID journalId) {
        JournalEntry entry = entryRepository.findByIdAndOrganizationId(journalId, organizationId)
                .orElseThrow(() -> LedgerException.notFound("Journal entry not found"));
        if (entry.getStatus() != JournalStatus.DRAFT) {
            throw LedgerException.businessRule("INVALID_STATUS", "Only draft journals can be submitted for approval");
        }
        validateBalancedLines(organizationId, entry.getLines());
        entry.setStatus(JournalStatus.PENDING_APPROVAL);
        entryRepository.save(entry);
    }

    @Transactional
    public void applyApprovalDecision(UUID organizationId, UUID journalId, UUID approverId, boolean approved) {
        JournalEntry entry = entryRepository.findByIdAndOrganizationId(journalId, organizationId)
                .orElseThrow(() -> LedgerException.notFound("Journal entry not found"));
        if (approved) {
            entry.markApproved(approverId);
            entry.setStatus(JournalStatus.DRAFT);
        } else {
            entry.setStatus(JournalStatus.DRAFT);
        }
        entryRepository.save(entry);
    }

    private JournalEntry buildAndSaveEntry(
            UUID organizationId, UUID actorUserId, LocalDate entryDate, String description, String reference,
            JournalSourceType sourceType, UUID sourceId, String businessEventKey,
            String currencyCode, BigDecimal explicitRate, List<JournalLineInput> lineInputs, boolean postNow
    ) {
        if (entryDate == null) {
            throw LedgerException.badRequest("ENTRY_DATE_REQUIRED", "Journal entry date is required");
        }
        if (description == null || description.isBlank()) {
            throw LedgerException.badRequest("DESCRIPTION_REQUIRED", "Journal entry description is required");
        }
        if (lineInputs == null || lineInputs.size() < 2) {
            throw LedgerException.businessRule("MINIMUM_LINES", "A journal entry must contain at least two lines");
        }

        Organization org = organizationService.getOrganizationEntity(organizationId);
        AccountingPeriod period = periodService.requireOpenPeriodForDate(organizationId, entryDate);
        String ccy = (currencyCode == null || currencyCode.isBlank())
                ? org.getBaseCurrency()
                : currencyCode.toUpperCase(Locale.ROOT);
        BigDecimal fxRate = organizationService.resolveExchangeRate(organizationId, ccy, entryDate, explicitRate);

        FinancialSettings settings = organizationService.getSettingsEntity(organizationId);
        String entryNumber = sequenceService.nextNumber(organizationId, "JOURNAL", settings.getJournalPrefix(), entryDate.getYear());

        JournalEntry entry = new JournalEntry(
                organizationId, entryNumber, entryDate, period.getId(), description.trim(),
                reference != null ? reference.trim() : null,
                postNow ? JournalStatus.POSTED : JournalStatus.DRAFT,
                sourceType, sourceId, businessEventKey, ccy, fxRate, entryDate, actorUserId
        );

        BigDecimal[] totals = attachValidatedLines(entry, org, lineInputs, postNow);
        entry.setTotals(totals[0], totals[1]);

        if (postNow) {
            entry.markPosted(actorUserId);
        }
        entryRepository.save(entry);

        if (postNow) {
            auditService.record(organizationId, "JOURNAL_POSTED", "JOURNAL_ENTRY", entry.getId().toString(),
                    "Posted journal " + entry.getEntryNumber() + " (" + sourceType + ")",
                    Map.of("total", MoneyUtil.toMoneyString(entry.getTotalDebit()), "currency", ccy));
            eventPublisher.publishEvent(new DomainEvents.JournalPostedEvent(
                    organizationId, entry.getId(), entry.getEntryNumber(), entry.getEntryDate(), entry.getTotalDebit()));
        }
        return entry;
    }

    private BigDecimal[] attachValidatedLines(JournalEntry entry, Organization org,
                                              List<JournalLineInput> inputs, boolean enforceBalance) {
        if (inputs.size() < 2) {
            throw LedgerException.businessRule("MINIMUM_LINES", "At least two journal lines are required");
        }
        BigDecimal totalDebit = MoneyUtil.ZERO;
        BigDecimal totalCredit = MoneyUtil.ZERO;
        String baseCcy = org.getBaseCurrency();
        int lineNum = 1;

        for (JournalLineInput input : inputs) {
            Account account = accountService.requirePostingAccount(org.getId(), input.accountId());
            BigDecimal origDebit = MoneyUtil.round(input.debit(), entry.getCurrencyCode());
            BigDecimal origCredit = MoneyUtil.round(input.credit(), entry.getCurrencyCode());

            BigDecimal baseDebit = input.baseDebitOverride() != null
                    ? MoneyUtil.round(input.baseDebitOverride(), baseCcy)
                    : MoneyUtil.convertToBase(origDebit, entry.getExchangeRate(), baseCcy);
            BigDecimal baseCredit = input.baseCreditOverride() != null
                    ? MoneyUtil.round(input.baseCreditOverride(), baseCcy)
                    : MoneyUtil.convertToBase(origCredit, entry.getExchangeRate(), baseCcy);

            if (MoneyUtil.isNegative(origDebit) || MoneyUtil.isNegative(origCredit)
                    || MoneyUtil.isNegative(baseDebit) || MoneyUtil.isNegative(baseCredit)) {
                throw LedgerException.businessRule("NEGATIVE_LINE_AMOUNT", "Journal line amounts cannot be negative");
            }
            if ((MoneyUtil.isPositive(origDebit) && MoneyUtil.isPositive(origCredit))
                    || (MoneyUtil.isPositive(baseDebit) && MoneyUtil.isPositive(baseCredit))) {
                throw LedgerException.businessRule("BOTH_SIDES_POSITIVE", "A journal line cannot have both positive debit and credit");
            }
            if (MoneyUtil.isZero(baseDebit) && MoneyUtil.isZero(baseCredit)) {
                throw LedgerException.businessRule("ZERO_LINE_AMOUNT", "Journal line must have a positive debit or credit amount");
            }

            JournalLine line = new JournalLine(
                    org.getId(), lineNum++, account.getId(), input.contactId(),
                    input.description(), entry.getCurrencyCode(),
                    origDebit, origCredit, baseDebit, baseCredit
            );
            entry.addLine(line);
            totalDebit = totalDebit.add(baseDebit);
            totalCredit = totalCredit.add(baseCredit);
        }

        if (enforceBalance && totalDebit.compareTo(totalCredit) != 0) {
            throw LedgerException.businessRule(
                    "UNBALANCED_JOURNAL",
                    "Journal entry does not balance: debits (" + MoneyUtil.toMoneyString(totalDebit)
                            + ") != credits (" + MoneyUtil.toMoneyString(totalCredit) + ")"
            );
        }
        if (enforceBalance && MoneyUtil.isZero(totalDebit)) {
            throw LedgerException.businessRule("ZERO_JOURNAL", "Journal entry total must be greater than zero");
        }
        return new BigDecimal[]{totalDebit, totalCredit};
    }

    private void validateBalancedLines(UUID organizationId, List<JournalLine> lines) {
        if (lines.size() < 2) {
            throw LedgerException.businessRule("MINIMUM_LINES", "A journal entry must contain at least two lines");
        }
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (JournalLine l : lines) {
            accountService.requirePostingAccount(organizationId, l.getAccountId());
            if (MoneyUtil.isPositive(l.getDebitAmount()) && MoneyUtil.isPositive(l.getCreditAmount())) {
                throw LedgerException.businessRule("BOTH_SIDES_POSITIVE", "Journal line cannot have both debit and credit");
            }
            totalDebit = totalDebit.add(l.getDebitAmount());
            totalCredit = totalCredit.add(l.getCreditAmount());
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw LedgerException.businessRule(
                    "UNBALANCED_JOURNAL",
                    "Journal entry does not balance: debits (" + MoneyUtil.toMoneyString(totalDebit)
                            + ") != credits (" + MoneyUtil.toMoneyString(totalCredit) + ")"
            );
        }
        if (MoneyUtil.isZero(totalDebit)) {
            throw LedgerException.businessRule("ZERO_JOURNAL", "Journal entry total must be greater than zero");
        }
    }

    private Map<UUID, Account> loadAccountMap(UUID organizationId) {
        Map<UUID, Account> map = new HashMap<>();
        for (Account a : accountRepository.findByOrganizationIdOrderByCodeAsc(organizationId)) {
            map.put(a.getId(), a);
        }
        return map;
    }

    public JournalEntryDto toDto(JournalEntry entry) {
        return toDtoWithMap(entry, loadAccountMap(entry.getOrganizationId()));
    }

    private JournalEntryDto toDtoWithMap(JournalEntry e, Map<UUID, Account> accountMap) {
        List<JournalLineDto> lineDtos = new ArrayList<>();
        for (JournalLine l : e.getLines()) {
            Account acc = accountMap.get(l.getAccountId());
            lineDtos.add(new JournalLineDto(
                    l.getId(), l.getLineNumber(), l.getAccountId(),
                    acc != null ? acc.getCode() : "",
                    acc != null ? acc.getName() : "",
                    l.getContactId(), l.getDescription(), l.getCurrencyCode(),
                    MoneyUtil.toMoneyString(l.getOriginalDebit()),
                    MoneyUtil.toMoneyString(l.getOriginalCredit()),
                    MoneyUtil.toMoneyString(l.getDebitAmount()),
                    MoneyUtil.toMoneyString(l.getCreditAmount())
            ));
        }
        return new JournalEntryDto(
                e.getId(), e.getEntryNumber(), e.getEntryDate(), e.getAccountingPeriodId(),
                e.getDescription(), e.getReference(), e.getStatus(), e.getSourceType(),
                e.getSourceId(), e.getCurrencyCode(), e.getExchangeRate().stripTrailingZeros().toPlainString(),
                MoneyUtil.toMoneyString(e.getTotalDebit()), MoneyUtil.toMoneyString(e.getTotalCredit()),
                e.getReversedEntryId(), e.getReversalEntryId(), e.getCreatedBy(), e.getPostedBy(),
                e.getPostedAt(), e.getCreatedAt(), lineDtos
        );
    }

    public record JournalLineInput(
            @jakarta.validation.constraints.NotNull UUID accountId,
            UUID contactId,
            String description,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal baseDebitOverride,
            BigDecimal baseCreditOverride
    ) {
        public static JournalLineInput debit(UUID accountId, UUID contactId, String description, BigDecimal amount) {
            return new JournalLineInput(accountId, contactId, description, amount, BigDecimal.ZERO, null, null);
        }

        public static JournalLineInput credit(UUID accountId, UUID contactId, String description, BigDecimal amount) {
            return new JournalLineInput(accountId, contactId, description, BigDecimal.ZERO, amount, null, null);
        }

        public static JournalLineInput baseDebit(UUID accountId, UUID contactId, String description, BigDecimal origAmount, BigDecimal baseAmount) {
            return new JournalLineInput(accountId, contactId, description, origAmount, BigDecimal.ZERO, baseAmount, BigDecimal.ZERO);
        }

        public static JournalLineInput baseCredit(UUID accountId, UUID contactId, String description, BigDecimal origAmount, BigDecimal baseAmount) {
            return new JournalLineInput(accountId, contactId, description, BigDecimal.ZERO, origAmount, BigDecimal.ZERO, baseAmount);
        }
    }

    public record CreateJournalRequest(
            @jakarta.validation.constraints.NotNull LocalDate entryDate,
            @jakarta.validation.constraints.NotBlank String description,
            String reference,
            String currencyCode,
            BigDecimal exchangeRate,
            Boolean postImmediately,
            Boolean openingBalance,
            @jakarta.validation.constraints.NotEmpty List<JournalLineInput> lines
    ) {}

    public record UpdateJournalRequest(
            LocalDate entryDate,
            String description,
            String reference,
            List<JournalLineInput> lines
    ) {}

    public record JournalLineDto(
            UUID id,
            int lineNumber,
            UUID accountId,
            String accountCode,
            String accountName,
            UUID contactId,
            String description,
            String currencyCode,
            String originalDebit,
            String originalCredit,
            String debitAmount,
            String creditAmount
    ) {}

    public record JournalEntryDto(
            UUID id,
            String entryNumber,
            LocalDate entryDate,
            UUID accountingPeriodId,
            String description,
            String reference,
            JournalStatus status,
            JournalSourceType sourceType,
            UUID sourceId,
            String currencyCode,
            String exchangeRate,
            String totalDebit,
            String totalCredit,
            UUID reversedEntryId,
            UUID reversalEntryId,
            UUID createdBy,
            UUID postedBy,
            Instant postedAt,
            Instant createdAt,
            List<JournalLineDto> lines
    ) {}
}
