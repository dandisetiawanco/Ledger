package com.ledger.journal;

import com.ledger.accounting.Account;
import com.ledger.accounting.AccountService;
import com.ledger.accounting.AccountingPeriod;
import com.ledger.accounting.AccountingPeriodRepository;
import com.ledger.shared.LedgerException;
import com.ledger.shared.MoneyUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class GeneralLedgerService {

    private final AccountService accountService;
    private final JournalLineRepository journalLineRepository;
    private final AccountingPeriodRepository periodRepository;

    public GeneralLedgerService(
            AccountService accountService,
            JournalLineRepository journalLineRepository,
            AccountingPeriodRepository periodRepository) {
        this.accountService = accountService;
        this.journalLineRepository = journalLineRepository;
        this.periodRepository = periodRepository;
    }

    @Transactional(readOnly = true)
    public GeneralLedgerResponse getLedger(
            UUID organizationId,
            UUID accountId,
            LocalDate fromDate,
            LocalDate toDate,
            UUID periodId,
            String search,
            int page,
            int size
    ) {
        Account account = accountService.requireAccount(organizationId, accountId);

        LocalDate effFrom = fromDate;
        LocalDate effTo = toDate;
        if (periodId != null) {
            AccountingPeriod period = periodRepository.findByIdAndOrganizationId(periodId, organizationId)
                    .orElseThrow(() -> LedgerException.notFound("Accounting period not found"));
            effFrom = period.getStartDate();
            effTo = period.getEndDate();
        }

        BigDecimal openingBalance = MoneyUtil.ZERO;
        if (effFrom != null) {
            List<Object[]> openingRows = journalLineRepository.sumPostedForAccountBefore(organizationId, accountId, effFrom);
            if (!openingRows.isEmpty() && openingRows.getFirst() != null) {
                BigDecimal opDebit = (BigDecimal) openingRows.getFirst()[0];
                BigDecimal opCredit = (BigDecimal) openingRows.getFirst()[1];
                openingBalance = account.getNormalBalance().netChange(opDebit, opCredit);
            }
        }

        String cleanSearch = (search == null || search.isBlank()) ? null : search.trim();
        List<JournalLine> lines = journalLineRepository.findPostedLinesForAccount(
                organizationId, accountId, effFrom, effTo, cleanSearch
        );

        BigDecimal running = openingBalance;
        BigDecimal periodDebit = MoneyUtil.ZERO;
        BigDecimal periodCredit = MoneyUtil.ZERO;
        List<LedgerEntryRow> allRows = new ArrayList<>(lines.size());

        for (JournalLine line : lines) {
            JournalEntry entry = line.getJournalEntry();
            BigDecimal debit = MoneyUtil.normalize(line.getDebitAmount());
            BigDecimal credit = MoneyUtil.normalize(line.getCreditAmount());
            running = running.add(account.getNormalBalance().netChange(debit, credit));
            periodDebit = periodDebit.add(debit);
            periodCredit = periodCredit.add(credit);

            allRows.add(new LedgerEntryRow(
                    line.getId(),
                    entry.getId(),
                    entry.getEntryNumber(),
                    entry.getEntryDate(),
                    entry.getDescription(),
                    line.getDescription(),
                    entry.getReference(),
                    entry.getSourceType().name(),
                    entry.getSourceId(),
                    entry.getStatus().name(),
                    MoneyUtil.toMoneyString(debit),
                    MoneyUtil.toMoneyString(credit),
                    MoneyUtil.toMoneyString(running)
            ));
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 500);
        int fromIdx = Math.min(safePage * safeSize, allRows.size());
        int toIdx = Math.min(fromIdx + safeSize, allRows.size());
        List<LedgerEntryRow> pageRows = allRows.subList(fromIdx, toIdx);

        return new GeneralLedgerResponse(
                account.getId(),
                account.getCode(),
                account.getName(),
                account.getAccountType().name(),
                account.getNormalBalance().name(),
                effFrom,
                effTo,
                MoneyUtil.toMoneyString(openingBalance),
                MoneyUtil.toMoneyString(periodDebit),
                MoneyUtil.toMoneyString(periodCredit),
                MoneyUtil.toMoneyString(running),
                allRows.size(),
                safePage,
                safeSize,
                pageRows
        );
    }

    public record LedgerEntryRow(
            UUID lineId,
            UUID journalEntryId,
            String entryNumber,
            LocalDate entryDate,
            String entryDescription,
            String lineDescription,
            String reference,
            String sourceType,
            UUID sourceId,
            String status,
            String debit,
            String credit,
            String runningBalance
    ) {}

    public record GeneralLedgerResponse(
            UUID accountId,
            String accountCode,
            String accountName,
            String accountType,
            String normalBalance,
            LocalDate fromDate,
            LocalDate toDate,
            String openingBalance,
            String periodDebitTotal,
            String periodCreditTotal,
            String closingBalance,
            int totalElements,
            int page,
            int size,
            List<LedgerEntryRow> rows
    ) {}
}
