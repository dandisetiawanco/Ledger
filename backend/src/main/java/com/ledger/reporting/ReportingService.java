package com.ledger.reporting;

import com.ledger.accounting.Account;
import com.ledger.accounting.AccountRepository;
import com.ledger.accounting.AccountType;
import com.ledger.accounting.CashFlowCategory;
import com.ledger.budget.BudgetService;
import com.ledger.contact.Contact;
import com.ledger.contact.ContactRepository;
import com.ledger.expense.Expense;
import com.ledger.expense.ExpenseRepository;
import com.ledger.invoice.CreditNote;
import com.ledger.invoice.CreditNoteRepository;
import com.ledger.invoice.Invoice;
import com.ledger.invoice.InvoiceRepository;
import com.ledger.journal.JournalEntry;
import com.ledger.journal.JournalEntryRepository;
import com.ledger.journal.JournalLine;
import com.ledger.journal.JournalLineRepository;
import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationService;
import com.ledger.payment.PaymentAllocation;
import com.ledger.payment.PaymentAllocationRepository;
import com.ledger.shared.MoneyUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportingService {

    private final AccountRepository accountRepository;
    private final JournalLineRepository journalLineRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final InvoiceRepository invoiceRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final ExpenseRepository expenseRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final ContactRepository contactRepository;
    private final OrganizationService organizationService;
    private final BudgetService budgetService;

    public ReportingService(
            AccountRepository accountRepository,
            JournalLineRepository journalLineRepository,
            JournalEntryRepository journalEntryRepository,
            InvoiceRepository invoiceRepository,
            CreditNoteRepository creditNoteRepository,
            ExpenseRepository expenseRepository,
            PaymentAllocationRepository allocationRepository,
            ContactRepository contactRepository,
            OrganizationService organizationService,
            BudgetService budgetService) {
        this.accountRepository = accountRepository;
        this.journalLineRepository = journalLineRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.invoiceRepository = invoiceRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.expenseRepository = expenseRepository;
        this.allocationRepository = allocationRepository;
        this.contactRepository = contactRepository;
        this.organizationService = organizationService;
        this.budgetService = budgetService;
    }

    @Transactional(readOnly = true)
    public TrialBalanceReport trialBalance(UUID orgId, LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        Organization org = organizationService.getOrganizationEntity(orgId);
        List<Account> accounts = accountRepository.findByOrganizationIdOrderByCodeAsc(orgId);
        Map<UUID, BigDecimal[]> sums = loadSumsUpTo(orgId, effectiveDate);

        List<TrialBalanceRow> rows = new ArrayList<>();
        BigDecimal totalDebit = MoneyUtil.ZERO;
        BigDecimal totalCredit = MoneyUtil.ZERO;

        for (Account acc : accounts) {
            if (!acc.isAllowPosting()) continue;
            BigDecimal[] pair = sums.get(acc.getId());
            if (pair == null) continue;
            BigDecimal netDebit = pair[0].subtract(pair[1]);
            if (MoneyUtil.isZero(netDebit)) continue;

            BigDecimal debitBal = MoneyUtil.isPositive(netDebit) ? MoneyUtil.normalize(netDebit) : MoneyUtil.ZERO;
            BigDecimal creditBal = MoneyUtil.isNegative(netDebit) ? MoneyUtil.normalize(netDebit.abs()) : MoneyUtil.ZERO;
            totalDebit = totalDebit.add(debitBal);
            totalCredit = totalCredit.add(creditBal);

            rows.add(new TrialBalanceRow(
                    acc.getId(), acc.getCode(), acc.getName(), acc.getAccountType().name(),
                    MoneyUtil.toMoneyString(debitBal), MoneyUtil.toMoneyString(creditBal)
            ));
        }

        boolean balanced = totalDebit.compareTo(totalCredit) == 0;
        return new TrialBalanceReport(
                org.getName(), org.getBaseCurrency(), effectiveDate,
                MoneyUtil.toMoneyString(totalDebit), MoneyUtil.toMoneyString(totalCredit),
                balanced, rows
        );
    }

    @Transactional(readOnly = true)
    public ProfitAndLossReport profitAndLoss(UUID orgId, LocalDate fromDate, LocalDate toDate,
                                             LocalDate compareFrom, LocalDate compareTo) {
        LocalDate end = toDate != null ? toDate : LocalDate.now();
        LocalDate start = fromDate != null ? fromDate : end.withDayOfMonth(1);
        Organization org = organizationService.getOrganizationEntity(orgId);
        List<Account> accounts = accountRepository.findByOrganizationIdOrderByCodeAsc(orgId);

        Map<UUID, BigDecimal[]> currentSums = loadSumsInRange(orgId, start, end);
        Map<UUID, BigDecimal[]> compareSums = (compareFrom != null && compareTo != null)
                ? loadSumsInRange(orgId, compareFrom, compareTo)
                : null;

        List<StatementLine> revenueLines = new ArrayList<>();
        List<StatementLine> expenseLines = new ArrayList<>();
        BigDecimal totalRevenue = MoneyUtil.ZERO;
        BigDecimal totalExpense = MoneyUtil.ZERO;
        BigDecimal compTotalRevenue = MoneyUtil.ZERO;
        BigDecimal compTotalExpense = MoneyUtil.ZERO;

        for (Account acc : accounts) {
            if (!acc.isAllowPosting()) continue;
            if (acc.getAccountType() != AccountType.REVENUE && acc.getAccountType() != AccountType.EXPENSE) continue;

            BigDecimal curr = signedForStatement(acc, currentSums.get(acc.getId()));
            BigDecimal comp = compareSums != null ? signedForStatement(acc, compareSums.get(acc.getId())) : null;

            if (MoneyUtil.isZero(curr) && (comp == null || MoneyUtil.isZero(comp))) continue;

            StatementLine line = new StatementLine(
                    acc.getId(), acc.getCode(), acc.getName(),
                    MoneyUtil.toMoneyString(curr),
                    comp != null ? MoneyUtil.toMoneyString(comp) : null
            );

            if (acc.getAccountType() == AccountType.REVENUE) {
                revenueLines.add(line);
                totalRevenue = totalRevenue.add(curr);
                if (comp != null) compTotalRevenue = compTotalRevenue.add(comp);
            } else {
                expenseLines.add(line);
                totalExpense = totalExpense.add(curr);
                if (comp != null) compTotalExpense = compTotalExpense.add(comp);
            }
        }

        BigDecimal netProfit = totalRevenue.subtract(totalExpense);
        BigDecimal compNetProfit = compareSums != null ? compTotalRevenue.subtract(compTotalExpense) : null;

        return new ProfitAndLossReport(
                org.getName(), org.getBaseCurrency(), start, end, compareFrom, compareTo,
                revenueLines, expenseLines,
                MoneyUtil.toMoneyString(totalRevenue),
                MoneyUtil.toMoneyString(totalExpense),
                MoneyUtil.toMoneyString(netProfit),
                compareSums != null ? MoneyUtil.toMoneyString(compTotalRevenue) : null,
                compareSums != null ? MoneyUtil.toMoneyString(compTotalExpense) : null,
                compNetProfit != null ? MoneyUtil.toMoneyString(compNetProfit) : null
        );
    }

    @Transactional(readOnly = true)
    public BalanceSheetReport balanceSheet(UUID orgId, LocalDate asOfDate, LocalDate compareDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        Organization org = organizationService.getOrganizationEntity(orgId);
        List<Account> accounts = accountRepository.findByOrganizationIdOrderByCodeAsc(orgId);

        Map<UUID, BigDecimal[]> currentSums = loadSumsUpTo(orgId, effectiveDate);
        Map<UUID, BigDecimal[]> compareSums = compareDate != null ? loadSumsUpTo(orgId, compareDate) : null;

        List<StatementLine> assets = new ArrayList<>();
        List<StatementLine> liabilities = new ArrayList<>();
        List<StatementLine> equity = new ArrayList<>();

        BigDecimal totalAssets = MoneyUtil.ZERO;
        BigDecimal totalLiabilities = MoneyUtil.ZERO;
        BigDecimal totalEquity = MoneyUtil.ZERO;
        BigDecimal currentEarnings = MoneyUtil.ZERO;
        BigDecimal compareEarnings = MoneyUtil.ZERO;

        for (Account acc : accounts) {
            if (!acc.isAllowPosting()) continue;
            BigDecimal curr = signedForStatement(acc, currentSums.get(acc.getId()));
            BigDecimal comp = compareSums != null ? signedForStatement(acc, compareSums.get(acc.getId())) : null;

            switch (acc.getAccountType()) {
                case ASSET -> {
                    if (!MoneyUtil.isZero(curr) || (comp != null && !MoneyUtil.isZero(comp))) {
                        assets.add(new StatementLine(acc.getId(), acc.getCode(), acc.getName(),
                                MoneyUtil.toMoneyString(curr), comp != null ? MoneyUtil.toMoneyString(comp) : null));
                    }
                    totalAssets = totalAssets.add(curr);
                }
                case LIABILITY -> {
                    if (!MoneyUtil.isZero(curr) || (comp != null && !MoneyUtil.isZero(comp))) {
                        liabilities.add(new StatementLine(acc.getId(), acc.getCode(), acc.getName(),
                                MoneyUtil.toMoneyString(curr), comp != null ? MoneyUtil.toMoneyString(comp) : null));
                    }
                    totalLiabilities = totalLiabilities.add(curr);
                }
                case EQUITY -> {
                    if (!MoneyUtil.isZero(curr) || (comp != null && !MoneyUtil.isZero(comp))) {
                        equity.add(new StatementLine(acc.getId(), acc.getCode(), acc.getName(),
                                MoneyUtil.toMoneyString(curr), comp != null ? MoneyUtil.toMoneyString(comp) : null));
                    }
                    totalEquity = totalEquity.add(curr);
                }
                case REVENUE -> {
                    currentEarnings = currentEarnings.add(curr);
                    if (comp != null) compareEarnings = compareEarnings.add(comp);
                }
                case EXPENSE -> {
                    currentEarnings = currentEarnings.subtract(curr);
                    if (comp != null) compareEarnings = compareEarnings.subtract(comp);
                }
            }
        }

        if (!MoneyUtil.isZero(currentEarnings) || (compareSums != null && !MoneyUtil.isZero(compareEarnings))) {
            equity.add(new StatementLine(
                    null, "CURRENT", "Current Period Net Earnings",
                    MoneyUtil.toMoneyString(currentEarnings),
                    compareSums != null ? MoneyUtil.toMoneyString(compareEarnings) : null
            ));
            totalEquity = totalEquity.add(currentEarnings);
        }

        BigDecimal liabilitiesAndEquity = totalLiabilities.add(totalEquity);
        BigDecimal equationDifference = totalAssets.subtract(liabilitiesAndEquity);
        boolean balanced = equationDifference.compareTo(BigDecimal.ZERO) == 0;

        return new BalanceSheetReport(
                org.getName(), org.getBaseCurrency(), effectiveDate, compareDate,
                assets, liabilities, equity,
                MoneyUtil.toMoneyString(totalAssets),
                MoneyUtil.toMoneyString(totalLiabilities),
                MoneyUtil.toMoneyString(totalEquity),
                MoneyUtil.toMoneyString(liabilitiesAndEquity),
                MoneyUtil.toMoneyString(equationDifference),
                balanced
        );
    }

    /**
     * Direct-method Cash Flow Statement derived from posted cash/bank movements.
     * Internal transfers solely between CASH accounts are explicitly excluded from net cash flow.
     */
    @Transactional(readOnly = true)
    public CashFlowReport cashFlow(UUID orgId, LocalDate fromDate, LocalDate toDate) {
        LocalDate end = toDate != null ? toDate : LocalDate.now();
        LocalDate start = fromDate != null ? fromDate : end.withDayOfMonth(1);
        Organization org = organizationService.getOrganizationEntity(orgId);

        Map<UUID, Account> accountMap = new HashMap<>();
        List<UUID> cashAccountIds = new ArrayList<>();
        for (Account a : accountRepository.findByOrganizationIdOrderByCodeAsc(orgId)) {
            accountMap.put(a.getId(), a);
            if (a.isAllowPosting() && a.getCashFlowCategory() == CashFlowCategory.CASH) {
                cashAccountIds.add(a.getId());
            }
        }

        BigDecimal openingCash = MoneyUtil.ZERO;
        Map<UUID, BigDecimal[]> beforeSums = loadSumsUpTo(orgId, start.minusDays(1));
        for (UUID cashId : cashAccountIds) {
            BigDecimal[] pair = beforeSums.get(cashId);
            if (pair != null) {
                openingCash = openingCash.add(pair[0].subtract(pair[1]));
            }
        }

        Map<String, BigDecimal> operatingBuckets = new LinkedHashMap<>();
        Map<String, BigDecimal> investingBuckets = new LinkedHashMap<>();
        Map<String, BigDecimal> financingBuckets = new LinkedHashMap<>();
        BigDecimal excludedTransfers = MoneyUtil.ZERO;

        List<JournalEntry> entries = journalEntryRepository.findPostedInRange(orgId, start, end);
        for (JournalEntry entry : entries) {
            BigDecimal netCashDelta = MoneyUtil.ZERO;
            BigDecimal grossCashDebit = MoneyUtil.ZERO;
            List<JournalLine> nonCashLines = new ArrayList<>();

            for (JournalLine l : entry.getLines()) {
                Account acc = accountMap.get(l.getAccountId());
                if (acc != null && acc.getCashFlowCategory() == CashFlowCategory.CASH) {
                    netCashDelta = netCashDelta.add(l.getDebitAmount().subtract(l.getCreditAmount()));
                    grossCashDebit = grossCashDebit.add(l.getDebitAmount());
                } else {
                    nonCashLines.add(l);
                }
            }

            if (MoneyUtil.isZero(netCashDelta)) {
                if (MoneyUtil.isPositive(grossCashDebit) && nonCashLines.isEmpty()) {
                    excludedTransfers = excludedTransfers.add(grossCashDebit);
                }
                continue;
            }
            if (nonCashLines.isEmpty()) {
                continue;
            }

            // Classify the cash movement using the primary counterpart account's cash flow category.
            JournalLine primaryCounterpart = nonCashLines.stream()
                    .max(Comparator.comparing(l -> l.getDebitAmount().max(l.getCreditAmount())))
                    .orElse(nonCashLines.getFirst());
            Account counterAcc = accountMap.get(primaryCounterpart.getAccountId());
            CashFlowCategory category = (counterAcc != null && counterAcc.getCashFlowCategory() != null)
                    ? counterAcc.getCashFlowCategory()
                    : CashFlowCategory.OPERATING;

            String label = buildCashFlowLabel(counterAcc, netCashDelta);
            switch (category) {
                case INVESTING -> investingBuckets.merge(label, netCashDelta, BigDecimal::add);
                case FINANCING -> financingBuckets.merge(label, netCashDelta, BigDecimal::add);
                default -> operatingBuckets.merge(label, netCashDelta, BigDecimal::add);
            }
        }

        List<CashFlowLine> operatingLines = toCashFlowLines(operatingBuckets);
        List<CashFlowLine> investingLines = toCashFlowLines(investingBuckets);
        List<CashFlowLine> financingLines = toCashFlowLines(financingBuckets);

        BigDecimal netOperating = sumMap(operatingBuckets);
        BigDecimal netInvesting = sumMap(investingBuckets);
        BigDecimal netFinancing = sumMap(financingBuckets);
        BigDecimal netChange = netOperating.add(netInvesting).add(netFinancing);
        BigDecimal computedClosing = openingCash.add(netChange);

        BigDecimal ledgerClosing = MoneyUtil.ZERO;
        Map<UUID, BigDecimal[]> endSums = loadSumsUpTo(orgId, end);
        for (UUID cashId : cashAccountIds) {
            BigDecimal[] pair = endSums.get(cashId);
            if (pair != null) {
                ledgerClosing = ledgerClosing.add(pair[0].subtract(pair[1]));
            }
        }

        boolean reconciled = computedClosing.compareTo(ledgerClosing) == 0;
        return new CashFlowReport(
                org.getName(), org.getBaseCurrency(), start, end,
                operatingLines, investingLines, financingLines,
                MoneyUtil.toMoneyString(netOperating),
                MoneyUtil.toMoneyString(netInvesting),
                MoneyUtil.toMoneyString(netFinancing),
                MoneyUtil.toMoneyString(netChange),
                MoneyUtil.toMoneyString(openingCash),
                MoneyUtil.toMoneyString(ledgerClosing),
                MoneyUtil.toMoneyString(excludedTransfers),
                reconciled
        );
    }

    /**
     * Historical Accounts Receivable aging computed using invoice dates, payment allocation dates,
     * and credit note dates on or before asOfDate, reconciled against the GL AR control account.
     */
    @Transactional(readOnly = true)
    public AgingReport receivablesAging(UUID orgId, LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        Organization org = organizationService.getOrganizationEntity(orgId);
        String baseCcy = org.getBaseCurrency();

        List<Invoice> invoices = invoiceRepository.findPostedUpTo(orgId, effectiveDate);
        Map<UUID, BigDecimal> paidByInvoice = new HashMap<>();
        for (PaymentAllocation pa : allocationRepository.findActiveInvoiceAllocationsUpTo(orgId, effectiveDate)) {
            paidByInvoice.merge(pa.getInvoiceId(), pa.getAllocatedAmount(), BigDecimal::add);
        }
        Map<UUID, BigDecimal> creditedByInvoice = new HashMap<>();
        for (CreditNote cn : creditNoteRepository.findByOrganizationIdUpTo(orgId, effectiveDate)) {
            creditedByInvoice.merge(cn.getInvoiceId(), cn.getTotalAmount(), BigDecimal::add);
        }
        Map<UUID, Contact> contactMap = loadContactMap(orgId);

        List<AgingItemRow> items = new ArrayList<>();
        BigDecimal current = MoneyUtil.ZERO;
        BigDecimal d1To30 = MoneyUtil.ZERO;
        BigDecimal d31To60 = MoneyUtil.ZERO;
        BigDecimal d61To90 = MoneyUtil.ZERO;
        BigDecimal over90 = MoneyUtil.ZERO;

        for (Invoice inv : invoices) {
            BigDecimal paid = paidByInvoice.getOrDefault(inv.getId(), BigDecimal.ZERO);
            BigDecimal credited = creditedByInvoice.getOrDefault(inv.getId(), BigDecimal.ZERO);
            BigDecimal origOutstanding = MoneyUtil.round(inv.getTotalAmount().subtract(paid).subtract(credited), inv.getCurrencyCode());
            if (!MoneyUtil.isPositive(origOutstanding)) continue;

            BigDecimal baseOutstanding = MoneyUtil.convertToBase(origOutstanding, inv.getExchangeRate(), baseCcy);
            long daysPastDue = Math.max(0, ChronoUnit.DAYS.between(inv.getDueDate(), effectiveDate));
            String bucket = classifyBucket(daysPastDue);

            if (daysPastDue == 0) current = current.add(baseOutstanding);
            else if (daysPastDue <= 30) d1To30 = d1To30.add(baseOutstanding);
            else if (daysPastDue <= 60) d31To60 = d31To60.add(baseOutstanding);
            else if (daysPastDue <= 90) d61To90 = d61To90.add(baseOutstanding);
            else over90 = over90.add(baseOutstanding);

            Contact c = contactMap.get(inv.getCustomerId());
            items.add(new AgingItemRow(
                    inv.getId(), inv.getInvoiceNumber(), inv.getCustomerId(),
                    c != null ? c.getName() : "Unknown", inv.getIssueDate(), inv.getDueDate(),
                    (int) daysPastDue, bucket, inv.getCurrencyCode(),
                    MoneyUtil.toMoneyString(origOutstanding), MoneyUtil.toMoneyString(baseOutstanding)
            ));
        }

        BigDecimal totalSubledger = current.add(d1To30).add(d31To60).add(d61To90).add(over90);
        BigDecimal glControlBalance = getSystemAccountBalanceUpTo(orgId, "AR", effectiveDate);
        boolean reconciledWithGl = totalSubledger.compareTo(glControlBalance) == 0;

        return new AgingReport(
                "ACCOUNTS_RECEIVABLE", baseCcy, effectiveDate,
                MoneyUtil.toMoneyString(current), MoneyUtil.toMoneyString(d1To30),
                MoneyUtil.toMoneyString(d31To60), MoneyUtil.toMoneyString(d61To90),
                MoneyUtil.toMoneyString(over90), MoneyUtil.toMoneyString(totalSubledger),
                MoneyUtil.toMoneyString(glControlBalance), reconciledWithGl, items
        );
    }

    @Transactional(readOnly = true)
    public AgingReport payablesAging(UUID orgId, LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        Organization org = organizationService.getOrganizationEntity(orgId);
        String baseCcy = org.getBaseCurrency();

        List<Expense> bills = expenseRepository.findPostedSupplierBillsUpTo(orgId, effectiveDate);
        Map<UUID, BigDecimal> paidByBill = new HashMap<>();
        for (PaymentAllocation pa : allocationRepository.findActiveExpenseAllocationsUpTo(orgId, effectiveDate)) {
            paidByBill.merge(pa.getExpenseId(), pa.getAllocatedAmount(), BigDecimal::add);
        }
        Map<UUID, Contact> contactMap = loadContactMap(orgId);

        List<AgingItemRow> items = new ArrayList<>();
        BigDecimal current = MoneyUtil.ZERO;
        BigDecimal d1To30 = MoneyUtil.ZERO;
        BigDecimal d31To60 = MoneyUtil.ZERO;
        BigDecimal d61To90 = MoneyUtil.ZERO;
        BigDecimal over90 = MoneyUtil.ZERO;

        for (Expense bill : bills) {
            BigDecimal paid = paidByBill.getOrDefault(bill.getId(), BigDecimal.ZERO);
            BigDecimal origOutstanding = MoneyUtil.round(bill.getTotalAmount().subtract(paid), bill.getCurrencyCode());
            if (!MoneyUtil.isPositive(origOutstanding)) continue;

            BigDecimal baseOutstanding = MoneyUtil.convertToBase(origOutstanding, bill.getExchangeRate(), baseCcy);
            LocalDate due = bill.getDueDate() != null ? bill.getDueDate() : bill.getExpenseDate();
            long daysPastDue = Math.max(0, ChronoUnit.DAYS.between(due, effectiveDate));
            String bucket = classifyBucket(daysPastDue);

            if (daysPastDue == 0) current = current.add(baseOutstanding);
            else if (daysPastDue <= 30) d1To30 = d1To30.add(baseOutstanding);
            else if (daysPastDue <= 60) d31To60 = d31To60.add(baseOutstanding);
            else if (daysPastDue <= 90) d61To90 = d61To90.add(baseOutstanding);
            else over90 = over90.add(baseOutstanding);

            Contact s = bill.getSupplierId() != null ? contactMap.get(bill.getSupplierId()) : null;
            items.add(new AgingItemRow(
                    bill.getId(), bill.getExpenseNumber(), bill.getSupplierId(),
                    s != null ? s.getName() : "Supplier", bill.getExpenseDate(), due,
                    (int) daysPastDue, bucket, bill.getCurrencyCode(),
                    MoneyUtil.toMoneyString(origOutstanding), MoneyUtil.toMoneyString(baseOutstanding)
            ));
        }

        BigDecimal totalSubledger = current.add(d1To30).add(d31To60).add(d61To90).add(over90);
        BigDecimal glControlBalance = getSystemAccountBalanceUpTo(orgId, "AP", effectiveDate);
        boolean reconciledWithGl = totalSubledger.compareTo(glControlBalance) == 0;

        return new AgingReport(
                "ACCOUNTS_PAYABLE", baseCcy, effectiveDate,
                MoneyUtil.toMoneyString(current), MoneyUtil.toMoneyString(d1To30),
                MoneyUtil.toMoneyString(d31To60), MoneyUtil.toMoneyString(d61To90),
                MoneyUtil.toMoneyString(over90), MoneyUtil.toMoneyString(totalSubledger),
                MoneyUtil.toMoneyString(glControlBalance), reconciledWithGl, items
        );
    }

    @Transactional(readOnly = true)
    public OverviewDto overview(UUID orgId, LocalDate fromDate, LocalDate toDate) {
        LocalDate end = toDate != null ? toDate : LocalDate.now();
        LocalDate start = fromDate != null ? fromDate : end.withDayOfMonth(1);
        Organization org = organizationService.getOrganizationEntity(orgId);

        ProfitAndLossReport pnl = profitAndLoss(orgId, start, end, null, null);
        CashFlowReport cf = cashFlow(orgId, start, end);
        AgingReport ar = receivablesAging(orgId, end);
        AgingReport ap = payablesAging(orgId, end);

        // 6-month revenue & expense trend ending at 'end' month
        DateTimeFormatter ymFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        YearMonth endYm = YearMonth.from(end);
        List<MonthlyTrendPoint> trend = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = endYm.minusMonths(i);
            ProfitAndLossReport mPnl = profitAndLoss(orgId, ym.atDay(1), ym.atEndOfMonth(), null, null);
            trend.add(new MonthlyTrendPoint(
                    ym.format(ymFmt),
                    mPnl.totalRevenue(),
                    mPnl.totalExpenses(),
                    mPnl.netProfit()
            ));
        }

        Map<UUID, Contact> contacts = loadContactMap(orgId);
        List<UpcomingInvoiceDto> upcoming = invoiceRepository.findOpenReceivables(orgId).stream()
                .limit(8)
                .map(inv -> new UpcomingInvoiceDto(
                        inv.getId(), inv.getInvoiceNumber(),
                        contacts.containsKey(inv.getCustomerId()) ? contacts.get(inv.getCustomerId()).getName() : "Customer",
                        inv.getDueDate(), inv.isOverdueOn(end),
                        inv.getCurrencyCode(), MoneyUtil.toMoneyString(inv.getOutstandingBalance())
                ))
                .toList();

        List<RecentTransactionDto> recent = journalEntryRepository.findPostedInRange(orgId, end.minusMonths(6), end).stream()
                .sorted(Comparator.comparing(JournalEntry::getEntryDate).reversed().thenComparing(JournalEntry::getCreatedAt).reversed())
                .limit(10)
                .map(j -> new RecentTransactionDto(
                        j.getId(), j.getEntryNumber(), j.getEntryDate(),
                        j.getDescription(), j.getSourceType().name(),
                        MoneyUtil.toMoneyString(j.getTotalDebit())
                ))
                .toList();

        List<BudgetService.BudgetDetailDto> budgets = budgetService.listBudgets(orgId);
        BudgetSummaryDto budgetSummary = budgets.isEmpty() ? null : new BudgetSummaryDto(
                budgets.getFirst().id(), budgets.getFirst().name(),
                budgets.getFirst().totalPlanned(), budgets.getFirst().totalActual(),
                budgets.getFirst().overallUtilizationPercent()
        );

        return new OverviewDto(
                org.getName(), org.getBaseCurrency(), org.isDemo(), start, end,
                cf.closingCashBalance(),
                pnl.totalRevenue(),
                pnl.totalExpenses(),
                pnl.netProfit(),
                ar.totalOutstanding(),
                ap.totalOutstanding(),
                cf.netCashChange(),
                trend,
                pnl.expenseLines(),
                pnl.revenueLines(),
                upcoming,
                recent,
                budgetSummary
        );
    }

    private BigDecimal getSystemAccountBalanceUpTo(UUID orgId, String systemCode, LocalDate asOfDate) {
        return accountRepository.findByOrganizationIdAndSystemCode(orgId, systemCode)
                .map(acc -> {
                    Map<UUID, BigDecimal[]> sums = loadSumsUpTo(orgId, asOfDate);
                    return signedForStatement(acc, sums.get(acc.getId()));
                })
                .orElse(MoneyUtil.ZERO);
    }

    private Map<UUID, BigDecimal[]> loadSumsUpTo(UUID orgId, LocalDate asOfDate) {
        Map<UUID, BigDecimal[]> map = new HashMap<>();
        for (Object[] row : journalLineRepository.sumPostedByAccountUpTo(orgId, asOfDate)) {
            map.put((UUID) row[0], new BigDecimal[]{(BigDecimal) row[1], (BigDecimal) row[2]});
        }
        return map;
    }

    private Map<UUID, BigDecimal[]> loadSumsInRange(UUID orgId, LocalDate fromDate, LocalDate toDate) {
        Map<UUID, BigDecimal[]> map = new HashMap<>();
        for (Object[] row : journalLineRepository.sumPostedByAccountInRange(orgId, fromDate, toDate)) {
            map.put((UUID) row[0], new BigDecimal[]{(BigDecimal) row[1], (BigDecimal) row[2]});
        }
        return map;
    }

    /**
     * Uses classification-level sign (Asset/Expense = debit - credit; Liability/Equity/Revenue = credit - debit)
     * so contra accounts (such as Accumulated Depreciation or Owner Drawings) naturally offset their section total.
     */
    private BigDecimal signedForStatement(Account acc, BigDecimal[] pair) {
        if (pair == null) return MoneyUtil.ZERO;
        BigDecimal d = pair[0];
        BigDecimal c = pair[1];
        return (acc.getAccountType() == AccountType.ASSET || acc.getAccountType() == AccountType.EXPENSE)
                ? MoneyUtil.normalize(d.subtract(c))
                : MoneyUtil.normalize(c.subtract(d));
    }

    private String buildCashFlowLabel(Account counterAcc, BigDecimal delta) {
        if (counterAcc == null) {
            return MoneyUtil.isPositive(delta) ? "Other Cash Receipts" : "Other Cash Payments";
        }
        String sys = counterAcc.getSystemCode() != null ? counterAcc.getSystemCode() : "";
        return switch (sys) {
            case "AR", "SERVICE_REVENUE", "RETAINER_REVENUE", "CUSTOMER_CREDIT" -> "Cash received from customers";
            case "AP" -> "Cash paid to suppliers";
            case "OWNER_CAPITAL" -> "Owner capital contributions";
            case "OWNER_DRAWINGS" -> "Owner drawings and distributions";
            case "EQUIPMENT" -> "Purchase or disposal of equipment";
            default -> (MoneyUtil.isPositive(delta) ? "Cash inflow: " : "Cash paid for ") + counterAcc.getName();
        };
    }

    private List<CashFlowLine> toCashFlowLines(Map<String, BigDecimal> map) {
        return map.entrySet().stream()
                .filter(e -> !MoneyUtil.isZero(e.getValue()))
                .map(e -> new CashFlowLine(e.getKey(), MoneyUtil.toMoneyString(e.getValue())))
                .toList();
    }

    private BigDecimal sumMap(Map<String, BigDecimal> map) {
        return map.values().stream().reduce(MoneyUtil.ZERO, BigDecimal::add);
    }

    private String classifyBucket(long daysPastDue) {
        if (daysPastDue == 0) return "CURRENT";
        if (daysPastDue <= 30) return "DAYS_1_30";
        if (daysPastDue <= 60) return "DAYS_31_60";
        if (daysPastDue <= 90) return "DAYS_61_90";
        return "OVER_90";
    }

    private Map<UUID, Contact> loadContactMap(UUID orgId) {
        Map<UUID, Contact> map = new HashMap<>();
        for (Contact c : contactRepository.findByOrganizationIdOrderByNameAsc(orgId)) {
            map.put(c.getId(), c);
        }
        return map;
    }

    public record TrialBalanceRow(UUID accountId, String accountCode, String accountName, String accountType, String debit, String credit) {}

    public record TrialBalanceReport(
            String organizationName,
            String currencyCode,
            LocalDate asOfDate,
            String totalDebit,
            String totalCredit,
            boolean balanced,
            List<TrialBalanceRow> rows
    ) {}

    public record StatementLine(UUID accountId, String accountCode, String accountName, String amount, String comparativeAmount) {}

    public record ProfitAndLossReport(
            String organizationName,
            String currencyCode,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate compareFromDate,
            LocalDate compareToDate,
            List<StatementLine> revenueLines,
            List<StatementLine> expenseLines,
            String totalRevenue,
            String totalExpenses,
            String netProfit,
            String comparativeTotalRevenue,
            String comparativeTotalExpenses,
            String comparativeNetProfit
    ) {}

    public record BalanceSheetReport(
            String organizationName,
            String currencyCode,
            LocalDate asOfDate,
            LocalDate compareAsOfDate,
            List<StatementLine> assets,
            List<StatementLine> liabilities,
            List<StatementLine> equity,
            String totalAssets,
            String totalLiabilities,
            String totalEquity,
            String totalLiabilitiesAndEquity,
            String equationDifference,
            boolean balanced
    ) {}

    public record CashFlowLine(String label, String amount) {}

    public record CashFlowReport(
            String organizationName,
            String currencyCode,
            LocalDate fromDate,
            LocalDate toDate,
            List<CashFlowLine> operatingActivities,
            List<CashFlowLine> investingActivities,
            List<CashFlowLine> financingActivities,
            String netOperatingCash,
            String netInvestingCash,
            String netFinancingCash,
            String netCashChange,
            String openingCashBalance,
            String closingCashBalance,
            String excludedInternalTransfers,
            boolean reconciled
    ) {}

    public record AgingItemRow(
            UUID documentId,
            String documentNumber,
            UUID contactId,
            String contactName,
            LocalDate issueDate,
            LocalDate dueDate,
            int daysPastDue,
            String bucket,
            String originalCurrency,
            String originalOutstanding,
            String baseOutstanding
    ) {}

    public record AgingReport(
            String reportType,
            String baseCurrency,
            LocalDate asOfDate,
            String current,
            String days1To30,
            String days31To60,
            String days61To90,
            String over90Days,
            String totalOutstanding,
            String glControlAccountBalance,
            boolean reconciledWithGl,
            List<AgingItemRow> items
    ) {}

    public record MonthlyTrendPoint(String month, String revenue, String expenses, String netProfit) {}

    public record UpcomingInvoiceDto(
            UUID id, String invoiceNumber, String customerName, LocalDate dueDate,
            boolean overdue, String currencyCode, String outstandingBalance
    ) {}

    public record RecentTransactionDto(
            UUID id, String entryNumber, LocalDate entryDate, String description, String sourceType, String amount
    ) {}

    public record BudgetSummaryDto(UUID id, String name, String totalPlanned, String totalActual, String utilizationPercent) {}

    public record OverviewDto(
            String organizationName,
            String currencyCode,
            boolean demo,
            LocalDate fromDate,
            LocalDate toDate,
            String cashBalance,
            String periodRevenue,
            String periodExpenses,
            String periodNetProfit,
            String outstandingReceivables,
            String outstandingPayables,
            String periodNetCashMovement,
            List<MonthlyTrendPoint> monthlyTrend,
            List<StatementLine> expenseBreakdown,
            List<StatementLine> revenueBreakdown,
            List<UpcomingInvoiceDto> upcomingInvoices,
            List<RecentTransactionDto> recentTransactions,
            BudgetSummaryDto activeBudget
    ) {}
}
