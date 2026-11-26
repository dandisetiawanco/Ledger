package com.ledger.reconciliation;

import com.ledger.accounting.Account;
import com.ledger.accounting.AccountService;
import com.ledger.audit.AuditService;
import com.ledger.banking.BankAccount;
import com.ledger.banking.BankStatementLine;
import com.ledger.banking.BankStatementLineRepository;
import com.ledger.banking.BankingService;
import com.ledger.journal.JournalEntry;
import com.ledger.journal.JournalLine;
import com.ledger.journal.JournalLineRepository;
import com.ledger.journal.JournalService;
import com.ledger.journal.JournalSourceType;
import com.ledger.shared.LedgerException;
import com.ledger.shared.LedgerPrincipal;
import com.ledger.shared.MoneyUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    private final BankingService bankingService;
    private final BankStatementLineRepository statementLineRepository;
    private final JournalLineRepository journalLineRepository;
    private final ReconciliationRecordRepository reconciliationRepository;
    private final AccountService accountService;
    private final JournalService journalService;
    private final AuditService auditService;

    public ReconciliationService(
            BankingService bankingService,
            BankStatementLineRepository statementLineRepository,
            JournalLineRepository journalLineRepository,
            ReconciliationRecordRepository reconciliationRepository,
            AccountService accountService,
            JournalService journalService,
            AuditService auditService) {
        this.bankingService = bankingService;
        this.statementLineRepository = statementLineRepository;
        this.journalLineRepository = journalLineRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.accountService = accountService;
        this.journalService = journalService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ReconciliationWorkspaceDto getWorkspace(UUID organizationId, UUID bankAccountId) {
        BankAccount ba = bankingService.requireBankAccount(organizationId, bankAccountId);
        List<BankStatementLine> unmatchedStmtLines = statementLineRepository
                .findByOrganizationIdAndBankAccountIdAndReconciledFalseOrderByTransactionDateDesc(organizationId, bankAccountId);

        List<ReconciliationRecord> activeMatches = reconciliationRepository
                .findByOrganizationIdAndBankAccountIdAndStatus(organizationId, bankAccountId, "MATCHED");
        Set<UUID> reconciledJournalLineIds = activeMatches.stream()
                .map(ReconciliationRecord::getJournalLineId)
                .collect(Collectors.toSet());

        List<JournalLine> unmatchedJournalLines = journalLineRepository
                .findActivePostedLinesForAccount(organizationId, ba.getGlAccountId())
                .stream()
                .filter(jl -> !reconciledJournalLineIds.contains(jl.getId()))
                .toList();

        List<SuggestedMatchDto> suggestions = new ArrayList<>();
        for (BankStatementLine stmtLine : unmatchedStmtLines) {
            List<MatchCandidateDto> candidates = new ArrayList<>();
            for (JournalLine jl : unmatchedJournalLines) {
                BigDecimal signedLedgerAmt = jl.getDebitAmount().subtract(jl.getCreditAmount());
                if (signedLedgerAmt.compareTo(stmtLine.getAmount()) != 0) {
                    continue;
                }
                long daysApart = Math.abs(ChronoUnit.DAYS.between(stmtLine.getTransactionDate(), jl.getJournalEntry().getEntryDate()));
                if (daysApart > 10) {
                    continue;
                }
                int score = 70 + (int) Math.max(0, 20 - daysApart * 2);
                String reason = "Exact amount match, " + daysApart + " day(s) apart";
                String stmtRef = stmtLine.getReference() != null ? stmtLine.getReference().toLowerCase() : "";
                String jeRef = jl.getJournalEntry().getReference() != null ? jl.getJournalEntry().getReference().toLowerCase() : "";
                if (!stmtRef.isBlank() && !jeRef.isBlank() && (stmtRef.contains(jeRef) || jeRef.contains(stmtRef))) {
                    score += 10;
                    reason += ", reference match";
                }
                candidates.add(new MatchCandidateDto(
                        jl.getId(), jl.getJournalEntry().getId(), jl.getJournalEntry().getEntryNumber(),
                        jl.getJournalEntry().getEntryDate(), jl.getJournalEntry().getDescription(),
                        jl.getJournalEntry().getReference(), MoneyUtil.toMoneyString(signedLedgerAmt),
                        Math.min(score, 100), reason
                ));
            }
            candidates.sort(Comparator.comparingInt(MatchCandidateDto::confidenceScore).reversed());
            if (!candidates.isEmpty()) {
                suggestions.add(new SuggestedMatchDto(
                        stmtLine.getId(), stmtLine.getTransactionDate(), stmtLine.getDescription(),
                        stmtLine.getReference(), MoneyUtil.toMoneyString(stmtLine.getAmount()),
                        candidates.getFirst()
                ));
            }
        }

        List<UnmatchedJournalLineDto> journalDtos = unmatchedJournalLines.stream()
                .map(jl -> new UnmatchedJournalLineDto(
                        jl.getId(), jl.getJournalEntry().getId(), jl.getJournalEntry().getEntryNumber(),
                        jl.getJournalEntry().getEntryDate(), jl.getJournalEntry().getDescription(),
                        jl.getJournalEntry().getReference(),
                        MoneyUtil.toMoneyString(jl.getDebitAmount().subtract(jl.getCreditAmount()))
                ))
                .toList();

        List<ReconciliationHistoryDto> history = reconciliationRepository
                .findByOrganizationIdAndBankAccountIdOrderByReconciledAtDesc(organizationId, bankAccountId)
                .stream()
                .map(r -> new ReconciliationHistoryDto(
                        r.getId(), r.getBankStatementLineId(), r.getJournalLineId(),
                        MoneyUtil.toMoneyString(r.getMatchedAmount()), r.getMatchType(),
                        r.getStatus(), r.getNotes(), r.getReconciledAt(), r.getUnmatchedAt()
                ))
                .toList();

        return new ReconciliationWorkspaceDto(
                ba.getId(), ba.getName(), ba.getCurrencyCode(),
                unmatchedStmtLines.stream().map(BankingService.BankStatementLineDto::from).toList(),
                journalDtos, suggestions, history
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReconciliationHistoryDto confirmMatch(LedgerPrincipal principal, ConfirmMatchRequest req) {
        principal.requireFinanceOperations();
        UUID orgId = principal.requireOrganizationId();

        BankStatementLine stmtLine = statementLineRepository.findByIdAndOrganizationIdForUpdate(req.bankStatementLineId(), orgId)
                .orElseThrow(() -> LedgerException.notFound("Bank statement line not found"));
        if (stmtLine.isReconciled() || reconciliationRepository.existsByActiveBankLineId(stmtLine.getId())) {
            throw LedgerException.conflict("ALREADY_RECONCILED", "Bank statement line is already reconciled");
        }

        BankAccount ba = bankingService.requireBankAccount(orgId, stmtLine.getBankAccountId());
        JournalLine jl = journalLineRepository.findByIdAndOrganizationId(req.journalLineId(), orgId)
                .orElseThrow(() -> LedgerException.notFound("Journal line not found"));
        if (!jl.getAccountId().equals(ba.getGlAccountId())) {
            throw LedgerException.businessRule("ACCOUNT_MISMATCH", "Journal line does not belong to this bank account's linked GL account");
        }
        if (reconciliationRepository.existsByActiveJournalLineId(jl.getId())) {
            throw LedgerException.conflict("JOURNAL_LINE_RECONCILED", "Journal line is already reconciled with another statement line");
        }

        BigDecimal ledgerSigned = jl.getDebitAmount().subtract(jl.getCreditAmount());
        if (ledgerSigned.compareTo(stmtLine.getAmount()) != 0) {
            throw LedgerException.businessRule(
                    "AMOUNT_MISMATCH",
                    "Statement line (" + MoneyUtil.toMoneyString(stmtLine.getAmount())
                            + ") does not match journal line (" + MoneyUtil.toMoneyString(ledgerSigned) + ")"
            );
        }

        String matchType = (req.matchType() != null && !req.matchType().isBlank()) ? req.matchType().toUpperCase() : "MANUAL";
        ReconciliationRecord record = new ReconciliationRecord(
                orgId, ba.getId(), stmtLine.getId(), jl.getId(),
                stmtLine.getAmount(), matchType, req.notes(), principal.userId()
        );
        reconciliationRepository.save(record);

        stmtLine.setReconciled(true);
        statementLineRepository.save(stmtLine);

        auditService.record(orgId, "BANK_RECONCILED", "RECONCILIATION", record.getId().toString(),
                "Matched statement line " + stmtLine.getDescription() + " with journal " + jl.getJournalEntry().getEntryNumber(),
                Map.of("amount", MoneyUtil.toMoneyString(stmtLine.getAmount())));

        return new ReconciliationHistoryDto(
                record.getId(), record.getBankStatementLineId(), record.getJournalLineId(),
                MoneyUtil.toMoneyString(record.getMatchedAmount()), record.getMatchType(),
                record.getStatus(), record.getNotes(), record.getReconciledAt(), record.getUnmatchedAt()
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReconciliationHistoryDto createJournalAndReconcile(LedgerPrincipal principal, CreateAdjustmentReconcileRequest req) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();

        BankStatementLine stmtLine = statementLineRepository.findByIdAndOrganizationIdForUpdate(req.bankStatementLineId(), orgId)
                .orElseThrow(() -> LedgerException.notFound("Bank statement line not found"));
        if (stmtLine.isReconciled() || reconciliationRepository.existsByActiveBankLineId(stmtLine.getId())) {
            throw LedgerException.conflict("ALREADY_RECONCILED", "Bank statement line is already reconciled");
        }

        BankAccount ba = bankingService.requireBankAccount(orgId, stmtLine.getBankAccountId());
        Account offsetAccount = accountService.requirePostingAccount(orgId, req.offsetAccountId());

        BigDecimal absAmt = stmtLine.getAmount().abs();
        String desc = (req.description() != null && !req.description().isBlank())
                ? req.description().trim() : stmtLine.getDescription();

        List<JournalService.JournalLineInput> lines;
        if (MoneyUtil.isPositive(stmtLine.getAmount())) {
            lines = List.of(
                    JournalService.JournalLineInput.debit(ba.getGlAccountId(), null, desc, absAmt),
                    JournalService.JournalLineInput.credit(offsetAccount.getId(), null, desc, absAmt)
            );
        } else {
            lines = List.of(
                    JournalService.JournalLineInput.debit(offsetAccount.getId(), null, desc, absAmt),
                    JournalService.JournalLineInput.credit(ba.getGlAccountId(), null, desc, absAmt)
            );
        }

        JournalEntry je = journalService.postAutomatedJournal(
                orgId, principal.userId(), stmtLine.getTransactionDate(), desc, stmtLine.getReference(),
                JournalSourceType.BANK_ADJUSTMENT, stmtLine.getId(),
                "BANK_ADJ:" + stmtLine.getId(), ba.getCurrencyCode(), null, lines
        );

        JournalLine bankJournalLine = je.getLines().stream()
                .filter(l -> l.getAccountId().equals(ba.getGlAccountId()))
                .findFirst()
                .orElseThrow();

        ReconciliationRecord record = new ReconciliationRecord(
                orgId, ba.getId(), stmtLine.getId(), bankJournalLine.getId(),
                stmtLine.getAmount(), "CREATED_JOURNAL", "Created adjustment journal " + je.getEntryNumber(), principal.userId()
        );
        reconciliationRepository.save(record);

        stmtLine.setReconciled(true);
        statementLineRepository.save(stmtLine);

        auditService.record(orgId, "BANK_ADJUSTMENT_RECONCILED", "RECONCILIATION", record.getId().toString(),
                "Posted adjustment journal " + je.getEntryNumber() + " and reconciled bank statement line", Map.of());

        return new ReconciliationHistoryDto(
                record.getId(), record.getBankStatementLineId(), record.getJournalLineId(),
                MoneyUtil.toMoneyString(record.getMatchedAmount()), record.getMatchType(),
                record.getStatus(), record.getNotes(), record.getReconciledAt(), record.getUnmatchedAt()
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void unmatch(LedgerPrincipal principal, UUID reconciliationId) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();

        ReconciliationRecord record = reconciliationRepository.findByIdAndOrganizationId(reconciliationId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Reconciliation record not found"));
        if (!"MATCHED".equals(record.getStatus())) {
            throw LedgerException.businessRule("ALREADY_UNMATCHED", "Reconciliation record is already unmatched");
        }

        record.unmatch(principal.userId());
        reconciliationRepository.save(record);

        BankStatementLine stmtLine = statementLineRepository.findByIdAndOrganizationIdForUpdate(record.getBankStatementLineId(), orgId)
                .orElseThrow();
        stmtLine.setReconciled(false);
        statementLineRepository.save(stmtLine);

        auditService.record(orgId, "BANK_UNMATCHED", "RECONCILIATION", record.getId().toString(),
                "Unmatched bank reconciliation record", Map.of());
    }

    public record ConfirmMatchRequest(
            @jakarta.validation.constraints.NotNull UUID bankStatementLineId,
            @jakarta.validation.constraints.NotNull UUID journalLineId,
            String matchType,
            String notes
    ) {}

    public record CreateAdjustmentReconcileRequest(
            @jakarta.validation.constraints.NotNull UUID bankStatementLineId,
            @jakarta.validation.constraints.NotNull UUID offsetAccountId,
            String description
    ) {}

    public record MatchCandidateDto(
            UUID journalLineId,
            UUID journalEntryId,
            String entryNumber,
            LocalDate entryDate,
            String description,
            String reference,
            String amount,
            int confidenceScore,
            String matchReason
    ) {}

    public record SuggestedMatchDto(
            UUID bankStatementLineId,
            LocalDate transactionDate,
            String description,
            String reference,
            String amount,
            MatchCandidateDto bestCandidate
    ) {}

    public record UnmatchedJournalLineDto(
            UUID journalLineId,
            UUID journalEntryId,
            String entryNumber,
            LocalDate entryDate,
            String description,
            String reference,
            String amount
    ) {}

    public record ReconciliationHistoryDto(
            UUID id,
            UUID bankStatementLineId,
            UUID journalLineId,
            String matchedAmount,
            String matchType,
            String status,
            String notes,
            Instant reconciledAt,
            Instant unmatchedAt
    ) {}

    public record ReconciliationWorkspaceDto(
            UUID bankAccountId,
            String bankAccountName,
            String currencyCode,
            List<BankingService.BankStatementLineDto> unmatchedStatementLines,
            List<UnmatchedJournalLineDto> unmatchedJournalLines,
            List<SuggestedMatchDto> suggestedMatches,
            List<ReconciliationHistoryDto> history
    ) {}
}
