package com.ledger.accounting;

import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationInitializer;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Component
@Order(1)
public class AccountingInitializer implements OrganizationInitializer {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final AccountRepository accountRepository;
    private final FiscalYearRepository fiscalYearRepository;
    private final AccountingPeriodRepository periodRepository;

    public AccountingInitializer(
            AccountRepository accountRepository,
            FiscalYearRepository fiscalYearRepository,
            AccountingPeriodRepository periodRepository) {
        this.accountRepository = accountRepository;
        this.fiscalYearRepository = fiscalYearRepository;
        this.periodRepository = periodRepository;
    }

    @Override
    public void initializeOrganization(Organization org, UUID actorUserId) {
        UUID orgId = org.getId();
        String ccy = org.getBaseCurrency();

        // Top-level non-posting header accounts
        Account assets = save(new Account(orgId, "1000", "Assets", AccountType.ASSET, NormalBalance.DEBIT, null, "HEADER_ASSETS", null, "All business assets", ccy, false));
        Account liabilities = save(new Account(orgId, "2000", "Liabilities", AccountType.LIABILITY, NormalBalance.CREDIT, null, "HEADER_LIABILITIES", null, "All business obligations", ccy, false));
        Account equity = save(new Account(orgId, "3000", "Equity", AccountType.EQUITY, NormalBalance.CREDIT, null, "HEADER_EQUITY", null, "Owner equity and retained earnings", ccy, false));
        Account revenue = save(new Account(orgId, "4000", "Revenue", AccountType.REVENUE, NormalBalance.CREDIT, null, "HEADER_REVENUE", null, "Operating and other income", ccy, false));
        Account expenses = save(new Account(orgId, "5000", "Expenses", AccountType.EXPENSE, NormalBalance.DEBIT, null, "HEADER_EXPENSES", null, "Operating expenses", ccy, false));

        // Assets
        save(new Account(orgId, "1010", "Cash", AccountType.ASSET, NormalBalance.DEBIT, assets.getId(), "CASH", CashFlowCategory.CASH, "Petty cash and cash on hand", ccy, true));
        save(new Account(orgId, "1020", "Bank", AccountType.ASSET, NormalBalance.DEBIT, assets.getId(), "BANK", CashFlowCategory.CASH, "Primary operating bank account", ccy, true));
        save(new Account(orgId, "1100", "Accounts Receivable", AccountType.ASSET, NormalBalance.DEBIT, assets.getId(), "AR", CashFlowCategory.OPERATING, "Unpaid customer invoices", ccy, true));
        save(new Account(orgId, "1200", "Input Tax Receivable", AccountType.ASSET, NormalBalance.DEBIT, assets.getId(), "TAX_RECEIVABLE", CashFlowCategory.OPERATING, "Recoverable tax on expenses and bills", ccy, true));
        save(new Account(orgId, "1500", "Equipment", AccountType.ASSET, NormalBalance.DEBIT, assets.getId(), "EQUIPMENT", CashFlowCategory.INVESTING, "Computers, hardware, and office equipment", ccy, true));
        save(new Account(orgId, "1510", "Accumulated Depreciation", AccountType.ASSET, NormalBalance.CREDIT, assets.getId(), "ACCUM_DEPR", CashFlowCategory.INVESTING, "Contra-asset for equipment depreciation", ccy, true));

        // Liabilities
        save(new Account(orgId, "2010", "Accounts Payable", AccountType.LIABILITY, NormalBalance.CREDIT, liabilities.getId(), "AP", CashFlowCategory.OPERATING, "Unpaid supplier bills", ccy, true));
        save(new Account(orgId, "2100", "Tax Payable", AccountType.LIABILITY, NormalBalance.CREDIT, liabilities.getId(), "TAX_PAYABLE", CashFlowCategory.OPERATING, "Collected sales tax owed to authorities", ccy, true));
        save(new Account(orgId, "2200", "Customer Deposits & Unallocated Credits", AccountType.LIABILITY, NormalBalance.CREDIT, liabilities.getId(), "CUSTOMER_CREDIT", CashFlowCategory.OPERATING, "Excess customer receipts awaiting allocation", ccy, true));
        save(new Account(orgId, "2300", "Accrued Expenses", AccountType.LIABILITY, NormalBalance.CREDIT, liabilities.getId(), "ACCRUED_EXPENSE", CashFlowCategory.OPERATING, "Accrued obligations not yet billed", ccy, true));

        // Equity
        save(new Account(orgId, "3010", "Owner Capital", AccountType.EQUITY, NormalBalance.CREDIT, equity.getId(), "OWNER_CAPITAL", CashFlowCategory.FINANCING, "Owner capital contributions and opening equity", ccy, true));
        save(new Account(orgId, "3020", "Retained Earnings", AccountType.EQUITY, NormalBalance.CREDIT, equity.getId(), "RETAINED_EARNINGS", CashFlowCategory.FINANCING, "Accumulated net earnings from prior periods", ccy, true));
        save(new Account(orgId, "3030", "Owner Drawings", AccountType.EQUITY, NormalBalance.DEBIT, equity.getId(), "OWNER_DRAWINGS", CashFlowCategory.FINANCING, "Owner distributions and drawings", ccy, true));

        // Revenue
        save(new Account(orgId, "4010", "Service Revenue", AccountType.REVENUE, NormalBalance.CREDIT, revenue.getId(), "SERVICE_REVENUE", CashFlowCategory.OPERATING, "Core client project and service revenue", ccy, true));
        save(new Account(orgId, "4020", "Retainer Revenue", AccountType.REVENUE, NormalBalance.CREDIT, revenue.getId(), "RETAINER_REVENUE", CashFlowCategory.OPERATING, "Recurring monthly client retainers", ccy, true));
        save(new Account(orgId, "4100", "Realized Foreign Exchange Gain", AccountType.REVENUE, NormalBalance.CREDIT, revenue.getId(), "FX_GAIN", CashFlowCategory.OPERATING, "Realized gain on foreign currency settlements", ccy, true));

        // Expenses
        save(new Account(orgId, "5010", "Contractor Expense", AccountType.EXPENSE, NormalBalance.DEBIT, expenses.getId(), "CONTRACTOR_EXPENSE", CashFlowCategory.OPERATING, "External freelance and specialist contractors", ccy, true));
        save(new Account(orgId, "5020", "Software Subscription Expense", AccountType.EXPENSE, NormalBalance.DEBIT, expenses.getId(), "SOFTWARE_EXPENSE", CashFlowCategory.OPERATING, "Cloud infrastructure, SaaS tools, and licenses", ccy, true));
        save(new Account(orgId, "5030", "Marketing Expense", AccountType.EXPENSE, NormalBalance.DEBIT, expenses.getId(), "MARKETING_EXPENSE", CashFlowCategory.OPERATING, "Campaigns, brand production, and outreach", ccy, true));
        save(new Account(orgId, "5040", "Office Expense", AccountType.EXPENSE, NormalBalance.DEBIT, expenses.getId(), "OFFICE_EXPENSE", CashFlowCategory.OPERATING, "Office supplies, utilities, and consumables", ccy, true));
        save(new Account(orgId, "5050", "Workspace & Rent Expense", AccountType.EXPENSE, NormalBalance.DEBIT, expenses.getId(), "RENT_EXPENSE", CashFlowCategory.OPERATING, "Studio lease and workspace fees", ccy, true));
        save(new Account(orgId, "5060", "Bank Fees & Charges", AccountType.EXPENSE, NormalBalance.DEBIT, expenses.getId(), "BANK_FEES", CashFlowCategory.OPERATING, "Wire transfer and monthly bank administration fees", ccy, true));
        save(new Account(orgId, "5070", "Operating Expense", AccountType.EXPENSE, NormalBalance.DEBIT, expenses.getId(), "OPERATING_EXPENSE", CashFlowCategory.OPERATING, "General business operating expenses", ccy, true));
        save(new Account(orgId, "5100", "Realized Foreign Exchange Loss", AccountType.EXPENSE, NormalBalance.DEBIT, expenses.getId(), "FX_LOSS", CashFlowCategory.OPERATING, "Realized loss on foreign currency settlements", ccy, true));

        // Create current fiscal year (plus previous year if within first 3 months, and next year for forward-dated invoices)
        int currentYear = LocalDate.now().getYear();
        int startMonth = org.getFiscalYearStartMonth();
        createFiscalYearWithPeriods(orgId, currentYear - 1, startMonth);
        createFiscalYearWithPeriods(orgId, currentYear, startMonth);
        createFiscalYearWithPeriods(orgId, currentYear + 1, startMonth);
    }

    public FiscalYear createFiscalYearWithPeriods(UUID orgId, int startYear, int startMonth) {
        LocalDate start = LocalDate.of(startYear, startMonth, 1);
        LocalDate end = start.plusMonths(12).minusDays(1);
        String name = startMonth == 1
                ? "FY " + startYear
                : "FY " + startYear + "/" + (startYear + 1);
        if (fiscalYearRepository.existsByOrganizationIdAndName(orgId, name)) {
            return fiscalYearRepository.findByOrganizationIdOrderByStartDateDesc(orgId).stream()
                    .filter(fy -> fy.getName().equals(name))
                    .findFirst()
                    .orElseThrow();
        }
        FiscalYear fy = new FiscalYear(orgId, name, start, end);
        fiscalYearRepository.save(fy);

        for (int i = 0; i < 12; i++) {
            YearMonth ym = YearMonth.from(start.plusMonths(i));
            AccountingPeriod period = new AccountingPeriod(
                    orgId,
                    fy.getId(),
                    ym.format(MONTH_LABEL),
                    i + 1,
                    ym.atDay(1),
                    ym.atEndOfMonth()
            );
            periodRepository.save(period);
        }
        return fy;
    }

    private Account save(Account account) {
        return accountRepository.save(account);
    }
}
