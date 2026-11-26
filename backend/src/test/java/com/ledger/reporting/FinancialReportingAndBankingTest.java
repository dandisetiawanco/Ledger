package com.ledger.reporting;

import com.ledger.accounting.Account;
import com.ledger.accounting.AccountService;
import com.ledger.banking.BankingService;
import com.ledger.budget.BudgetService;
import com.ledger.contact.ContactService;
import com.ledger.contact.ContactType;
import com.ledger.demo.DemoSeedService;
import com.ledger.identity.User;
import com.ledger.identity.UserRepository;
import com.ledger.invoice.InvoiceService;
import com.ledger.journal.JournalService;
import com.ledger.organization.OrgRole;
import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationService;
import com.ledger.payment.PaymentMethod;
import com.ledger.payment.PaymentService;
import com.ledger.payment.PaymentType;
import com.ledger.reconciliation.ReconciliationService;
import com.ledger.shared.LedgerException;
import com.ledger.shared.LedgerPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class FinancialReportingAndBankingTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OrganizationService organizationService;
    @Autowired private AccountService accountService;
    @Autowired private ContactService contactService;
    @Autowired private InvoiceService invoiceService;
    @Autowired private PaymentService paymentService;
    @Autowired private JournalService journalService;
    @Autowired private BankingService bankingService;
    @Autowired private ReconciliationService reconciliationService;
    @Autowired private BudgetService budgetService;
    @Autowired private ReportingService reportingService;
    @Autowired private DemoSeedService demoSeedService;

    @Test
    void verifiesMultiCurrencyFxHistoricalAgingCashFlowExclusionBudgetAndReconciliation() {
        String s = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(new User("rep-" + s + "@test.local", passwordEncoder.encode("Secret123!"), "Reporter", false));
        Organization org = organizationService.createOrganizationInternal(user, "Report Co " + s, "Agency", "IDR", "Asia/Jakarta", "id-ID", 1, false);
        LedgerPrincipal owner = new LedgerPrincipal(user.getId(), user.getEmail(), user.getFullName(), false, UUID.randomUUID(), org.getId(), OrgRole.OWNER, false);

        Account bank = accountService.requireBySystemCode(org.getId(), "BANK");
        Account cash = accountService.requireBySystemCode(org.getId(), "CASH");
        Account rev = accountService.requireBySystemCode(org.getId(), "SERVICE_REVENUE");
        Account fxGain = accountService.requireBySystemCode(org.getId(), "FX_GAIN");
        Account bankFees = accountService.requireBySystemCode(org.getId(), "BANK_FEES");
        Account officeExp = accountService.requireBySystemCode(org.getId(), "OFFICE_EXPENSE");

        LocalDate today = LocalDate.now();
        LocalDate d1 = today.minusDays(45);
        LocalDate d2 = today.minusDays(15);

        // Bank account with opening balance 10,000,000
        var ba = bankingService.createBankAccount(owner, new BankingService.CreateBankAccountRequest(
                "Main Bank", "Demo Bank", "**** 1111", "IDR", bank.getId(), new BigDecimal("10000000"), d1
        ));

        // Internal cash transfer 1,000,000 (Bank -> Cash): MUST be excluded from net cash flow!
        journalService.createManualJournal(owner, new JournalService.CreateJournalRequest(
                d1.plusDays(1), "Transfer to petty cash", "XFER-01", "IDR", null, true, false,
                List.of(
                        JournalService.JournalLineInput.debit(cash.getId(), null, "Petty cash refill", new BigDecimal("1000000")),
                        JournalService.JournalLineInput.credit(bank.getId(), null, "Bank withdrawal", new BigDecimal("1000000"))
                )
        ));

        // USD customer invoice: $1,000 at 15,000 IDR/USD = 15,000,000 IDR on d1, due d1+10
        var usdCustomer = contactService.createContact(owner, new ContactService.CreateContactRequest(
                ContactType.CUSTOMER, "Overseas Client", "Alex", "alex@overseas.example", null, "SG", null, "USD", 10, null
        ));
        var inv = invoiceService.createDraft(owner, new InvoiceService.CreateInvoiceRequest(
                usdCustomer.id(), d1, d1.plusDays(10), 10, "USD", new BigDecimal("15000"), false,
                "USD Sprint", null, true,
                List.of(new InvoiceService.InvoiceLineRequest(null, "USD Advisory", rev.getId(), BigDecimal.ONE, new BigDecimal("1000"), BigDecimal.ZERO, null, BigDecimal.ZERO))
        ));

        // Settle $1,000 on d2 at 15,500 IDR/USD = 15,500,000 IDR -> Realized FX Gain = 500,000 IDR
        paymentService.recordPayment(owner, new PaymentService.RecordPaymentRequest(
                PaymentType.CUSTOMER_RECEIPT, usdCustomer.id(), d2, PaymentMethod.BANK_TRANSFER,
                bank.getId(), "USD", new BigDecimal("15500"), new BigDecimal("1000"), "WIRE-USD-1", null, "fx-pay-1",
                List.of(new PaymentService.AllocationInput(inv.id(), null, new BigDecimal("1000")))
        ), null);

        // Historical AR aging BEFORE payment (d1 + 20) MUST still show 15,000,000 overdue (1-30 days), matching GL AR!
        var pastAr = reportingService.receivablesAging(org.getId(), d1.plusDays(20));
        assertThat(pastAr.totalOutstanding()).isEqualTo("15000000.00");
        assertThat(pastAr.days1To30()).isEqualTo("15000000.00");
        assertThat(pastAr.reconciledWithGl()).isTrue();

        // Current AR aging AFTER payment shows 0.00
        var nowAr = reportingService.receivablesAging(org.getId(), today);
        assertThat(nowAr.totalOutstanding()).isEqualTo("0.00");
        assertThat(nowAr.reconciledWithGl()).isTrue();

        // Verify FX Gain posted and Balance Sheet / Trial Balance / Cash Flow reconcile
        var tb = reportingService.trialBalance(org.getId(), today);
        assertThat(tb.balanced()).isTrue();
        var bs = reportingService.balanceSheet(org.getId(), today, null);
        assertThat(bs.balanced()).isTrue();
        assertThat(bs.equationDifference()).isEqualTo("0.00");

        var pnl = reportingService.profitAndLoss(org.getId(), d1, today, null, null);
        assertThat(pnl.totalRevenue()).isEqualTo("15500000.00"); // 15M service + 500k realized FX gain
        assertThat(pnl.revenueLines()).anyMatch(l -> l.accountId().equals(fxGain.getId()) && l.amount().equals("500000.00"));

        var cf = reportingService.cashFlow(org.getId(), d1, today);
        assertThat(cf.reconciled()).isTrue();
        assertThat(cf.excludedInternalTransfers()).isEqualTo("1000000.00");
        assertThat(cf.closingCashBalance()).isEqualTo("25500000.00"); // 10M opening + 15.5M receipt

        // Budget with zero-budget category (no divide-by-zero!)
        var budget = budgetService.createBudget(owner, new BudgetService.CreateBudgetRequest(
                "Zero Safe Budget", null, "ANNUAL", d1, today, null,
                List.of(new BudgetService.BudgetLineInput(officeExp.getId(), null, BigDecimal.ZERO, "Zero budget test"))
        ));
        assertThat(budget.lines().getFirst().utilizationPercent()).isEqualTo("0.00");

        // Bank CSV import + duplicate detection + reconciliation
        String csv = "Date,Description,Reference,Debit,Credit\n"
                + d2 + ",Wire from Overseas Client,WIRE-USD-1,,15500000\n"
                + today + ",Monthly Bank Admin Fee,FEE-01,50000,\n";
        var firstImport = bankingService.commitCsvImport(owner, ba.id(), new BankingService.CsvImportRequest(
                "stmt.csv", csv, "Date", "Description", "Reference", "Debit", "Credit", null, "yyyy-MM-dd"
        ));
        assertThat(firstImport.importedRows()).isEqualTo(2);
        assertThat(firstImport.duplicateRows()).isEqualTo(0);

        // Re-importing same CSV detects 100% duplicates via deterministic fingerprints
        var dupImport = bankingService.commitCsvImport(owner, ba.id(), new BankingService.CsvImportRequest(
                "stmt.csv", csv, "Date", "Description", "Reference", "Debit", "Credit", null, "yyyy-MM-dd"
        ));
        assertThat(dupImport.importedRows()).isEqualTo(0);
        assertThat(dupImport.duplicateRows()).isEqualTo(2);

        // Suggested match exists for 15,500,000 receipt
        var ws = reconciliationService.getWorkspace(org.getId(), ba.id());
        assertThat(ws.suggestedMatches()).hasSize(1);
        var matchRec = reconciliationService.confirmMatch(owner, new ReconciliationService.ConfirmMatchRequest(
                ws.suggestedMatches().getFirst().bankStatementLineId(),
                ws.suggestedMatches().getFirst().bestCandidate().journalLineId(),
                "EXACT", "Confirmed match"
        ));
        assertThat(matchRec.status()).isEqualTo("MATCHED");

        // Duplicate match rejected
        assertThatThrownBy(() -> reconciliationService.confirmMatch(owner, new ReconciliationService.ConfirmMatchRequest(
                ws.suggestedMatches().getFirst().bankStatementLineId(),
                ws.suggestedMatches().getFirst().bestCandidate().journalLineId(),
                "MANUAL", "Duplicate attempt"
        ))).isInstanceOf(LedgerException.class);

        // Create adjustment journal for unmatched 50,000 fee line
        var feeLine = ws.unmatchedStatementLines().stream()
                .filter(l -> l.amount().equals("-50000.00")).findFirst().orElseThrow();
        var adjRec = reconciliationService.createJournalAndReconcile(owner, new ReconciliationService.CreateAdjustmentReconcileRequest(
                feeLine.id(), bankFees.getId(), "Bank admin fee"
        ));
        assertThat(adjRec.matchType()).isEqualTo("CREATED_JOURNAL");
    }

    @Test
    void demoSeedReconcilesCompletelyAcrossAllFinancialStatements() {
        var handle = demoSeedService.ensureDemoWorkspace();
        UUID orgId = handle.demoOrganization().getId();
        LocalDate today = LocalDate.now();

        var tb = reportingService.trialBalance(orgId, today);
        assertThat(tb.balanced()).isTrue();
        assertThat(new BigDecimal(tb.totalDebit())).isGreaterThan(BigDecimal.ZERO);

        var bs = reportingService.balanceSheet(orgId, today, null);
        assertThat(bs.balanced()).isTrue();
        assertThat(bs.equationDifference()).isEqualTo("0.00");

        var cf = reportingService.cashFlow(orgId, today.minusMonths(5).withDayOfMonth(1), today);
        assertThat(cf.reconciled()).isTrue();

        var ar = reportingService.receivablesAging(orgId, today);
        assertThat(ar.reconciledWithGl()).isTrue();

        var ap = reportingService.payablesAging(orgId, today);
        assertThat(ap.reconciledWithGl()).isTrue();
    }
}
