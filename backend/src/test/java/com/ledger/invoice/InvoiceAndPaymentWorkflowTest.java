package com.ledger.invoice;

import com.ledger.accounting.Account;
import com.ledger.accounting.AccountService;
import com.ledger.contact.ContactService;
import com.ledger.contact.ContactType;
import com.ledger.expense.ExpenseService;
import com.ledger.expense.ExpenseType;
import com.ledger.identity.User;
import com.ledger.identity.UserRepository;
import com.ledger.organization.OrgRole;
import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationService;
import com.ledger.payment.PaymentMethod;
import com.ledger.payment.PaymentService;
import com.ledger.payment.PaymentType;
import com.ledger.reporting.ReportingService;
import com.ledger.shared.LedgerException;
import com.ledger.shared.LedgerPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class InvoiceAndPaymentWorkflowTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OrganizationService organizationService;
    @Autowired private AccountService accountService;
    @Autowired private ContactService contactService;
    @Autowired private InvoiceService invoiceService;
    @Autowired private InvoicePdfService pdfService;
    @Autowired private PaymentService paymentService;
    @Autowired private ExpenseService expenseService;
    @Autowired private ReportingService reportingService;

    private LedgerPrincipal owner;
    private Organization org;
    private UUID customerId;
    private UUID supplierId;

    @BeforeEach
    void setup() {
        String s = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(new User("inv-" + s + "@test.local", passwordEncoder.encode("Secret123!"), "Owner", false));
        org = organizationService.createOrganizationInternal(user, "Workflow Co " + s, "Agency", "IDR", "Asia/Jakarta", "id-ID", 1, false);
        owner = new LedgerPrincipal(user.getId(), user.getEmail(), user.getFullName(), false, UUID.randomUUID(), org.getId(), OrgRole.OWNER, false);

        // Disable expense approval threshold for direct posting in unit accounting tests
        organizationService.updateOrganization(owner, new OrganizationService.UpdateOrganizationRequest(
                null, null, null, null, null, null, null, null, true,
                "INV", "VAT", BigDecimal.ZERO, false, 30, false, false, false, true,
                new BigDecimal("100000000"), null, null
        ));

        customerId = contactService.createContact(owner, new ContactService.CreateContactRequest(
                ContactType.CUSTOMER, "PT Karya Client", "Rudi", "rudi@karya.example", null, "Jakarta", null, "IDR", 30, null
        )).id();
        supplierId = contactService.createContact(owner, new ContactService.CreateContactRequest(
                ContactType.SUPPLIER, "CV Vendor Mandiri", "Dina", "dina@vendor.example", null, "Jakarta", null, "IDR", 30, null
        )).id();
    }

    @Test
    void executesAllFiveCanonicalAccountingWorkflowExamples() {
        Account bank = accountService.requireBySystemCode(org.getId(), "BANK");
        Account ar = accountService.requireBySystemCode(org.getId(), "AR");
        Account ap = accountService.requireBySystemCode(org.getId(), "AP");
        Account rev = accountService.requireBySystemCode(org.getId(), "SERVICE_REVENUE");
        Account opExp = accountService.requireBySystemCode(org.getId(), "OPERATING_EXPENSE");

        // 1. Service invoice: IDR 10,000,000 excluding tax -> Dr AR 10M, Cr Service Revenue 10M
        var inv = invoiceService.createDraft(owner, new InvoiceService.CreateInvoiceRequest(
                customerId, LocalDate.now(), LocalDate.now().plusDays(30), 30, "IDR", null, false,
                "Service engagement", null, true,
                List.of(new InvoiceService.InvoiceLineRequest(null, "Consulting", rev.getId(), BigDecimal.ONE, new BigDecimal("10000000"), BigDecimal.ZERO, null, BigDecimal.ZERO))
        ));
        assertThat(inv.totalAmount()).isEqualTo("10000000.00");
        assertThat(inv.outstandingBalance()).isEqualTo("10000000.00");

        // 2. Customer partial payment: IDR 4,000,000 -> Dr Bank 4M, Cr AR 4M; outstanding = 6,000,000
        paymentService.recordPayment(owner, new PaymentService.RecordPaymentRequest(
                PaymentType.CUSTOMER_RECEIPT, customerId, LocalDate.now(), PaymentMethod.BANK_TRANSFER,
                bank.getId(), "IDR", null, new BigDecimal("4000000"), "TRF-1", null, "pay-ex-1",
                List.of(new PaymentService.AllocationInput(inv.id(), null, new BigDecimal("4000000")))
        ), null);
        var afterPartial = invoiceService.getInvoice(org.getId(), inv.id());
        assertThat(afterPartial.status()).isEqualTo("PARTIALLY_PAID");
        assertThat(afterPartial.outstandingBalance()).isEqualTo("6000000.00");

        // 3. Operating expense paid immediately: IDR 500,000 -> Dr Operating Expense 500k, Cr Bank 500k
        expenseService.createExpense(owner, new ExpenseService.CreateExpenseRequest(
                ExpenseType.IMMEDIATE_EXPENSE, "Office utilities", LocalDate.now(), null,
                null, null, opExp.getId(), bank.getId(), "IDR", null,
                new BigDecimal("500000"), BigDecimal.ZERO, null, null, true
        ));

        // 4. Supplier bill: IDR 2,000,000 -> Dr Operating Expense 2M, Cr Accounts Payable 2M
        var bill = expenseService.createExpense(owner, new ExpenseService.CreateExpenseRequest(
                ExpenseType.SUPPLIER_BILL, "Design contractor bill", LocalDate.now(), LocalDate.now().plusDays(14),
                supplierId, null, opExp.getId(), null, "IDR", null,
                new BigDecimal("2000000"), BigDecimal.ZERO, null, null, true
        ));
        assertThat(bill.outstandingBalance()).isEqualTo("2000000.00");

        // 5. Supplier payment: IDR 2,000,000 -> Dr AP 2M, Cr Bank 2M (never double-counted as expense!)
        paymentService.recordPayment(owner, new PaymentService.RecordPaymentRequest(
                PaymentType.SUPPLIER_PAYMENT, supplierId, LocalDate.now(), PaymentMethod.BANK_TRANSFER,
                bank.getId(), "IDR", null, new BigDecimal("2000000"), "SPAY-1", null, "spay-ex-1",
                List.of(new PaymentService.AllocationInput(null, bill.id(), new BigDecimal("2000000")))
        ), null);

        var accounts = accountService.listAccounts(org.getId(), LocalDate.now(), false);
        assertThat(balanceOf(accounts, bank.getId())).isEqualTo("1500000.00"); // 4,000,000 - 500,000 - 2,000,000
        assertThat(balanceOf(accounts, ar.getId())).isEqualTo("6000000.00");   // 10,000,000 - 4,000,000
        assertThat(balanceOf(accounts, ap.getId())).isEqualTo("0.00");         // 2,000,000 - 2,000,000
        assertThat(balanceOf(accounts, rev.getId())).isEqualTo("10000000.00");
        assertThat(balanceOf(accounts, opExp.getId())).isEqualTo("2500000.00"); // 500,000 + 2,000,000 (not 4,500,000!)

        var tb = reportingService.trialBalance(org.getId(), LocalDate.now());
        assertThat(tb.balanced()).isTrue();
        var bs = reportingService.balanceSheet(org.getId(), LocalDate.now(), null);
        assertThat(bs.balanced()).isTrue();
    }

    @Test
    void handlesDiscountsTaxExclusiveTaxInclusiveCreditNotesOverpaymentAndIdempotency() {
        Account bank = accountService.requireBySystemCode(org.getId(), "BANK");
        Account rev = accountService.requireBySystemCode(org.getId(), "SERVICE_REVENUE");
        Account taxPayable = accountService.requireBySystemCode(org.getId(), "TAX_PAYABLE");
        Account custCredit = accountService.requireBySystemCode(org.getId(), "CUSTOMER_CREDIT");

        // Tax-exclusive invoice with 10% discount and 11% tax
        var invExcl = invoiceService.createDraft(owner, new InvoiceService.CreateInvoiceRequest(
                customerId, LocalDate.now(), LocalDate.now().plusDays(15), 15, "IDR", null, false,
                null, null, true,
                List.of(new InvoiceService.InvoiceLineRequest(null, "Project", rev.getId(), new BigDecimal("2"), new BigDecimal("5000000"), new BigDecimal("10"), null, new BigDecimal("11")))
        ));
        // Gross = 10,000,000; Discount 10% = 1,000,000; Net = 9,000,000; Tax 11% = 990,000; Total = 9,990,000
        assertThat(invExcl.subtotal()).isEqualTo("10000000.00");
        assertThat(invExcl.discountTotal()).isEqualTo("1000000.00");
        assertThat(invExcl.taxTotal()).isEqualTo("990000.00");
        assertThat(invExcl.totalAmount()).isEqualTo("9990000.00");

        // Verify tax was NOT posted as revenue
        var accts = accountService.listAccounts(org.getId(), LocalDate.now(), false);
        assertThat(balanceOf(accts, rev.getId())).isEqualTo("9000000.00");
        assertThat(balanceOf(accts, taxPayable.getId())).isEqualTo("990000.00");

        // Credit note reduces invoice outstanding balance
        invoiceService.issueCreditNote(owner, invExcl.id(), new InvoiceService.CreateCreditNoteRequest(
                LocalDate.now(), "Discount adjustment", new BigDecimal("900000"), new BigDecimal("99000")
        ));
        var afterCn = invoiceService.getInvoice(org.getId(), invExcl.id());
        assertThat(afterCn.outstandingBalance()).isEqualTo("8991000.00");

        // Attempting to over-allocate against invoice fails cleanly
        assertThatThrownBy(() -> paymentService.recordPayment(owner, new PaymentService.RecordPaymentRequest(
                PaymentType.CUSTOMER_RECEIPT, customerId, LocalDate.now(), PaymentMethod.BANK_TRANSFER,
                bank.getId(), "IDR", null, new BigDecimal("9500000"), "OVER-1", null, "over-alloc-1",
                List.of(new PaymentService.AllocationInput(invExcl.id(), null, new BigDecimal("9500000")))
        ), null)).isInstanceOf(LedgerException.class)
                .hasMessageContaining("exceeds invoice");

        // Legitimate overpayment: pays 9,500,000 allocating 8,991,000 -> 509,000 unallocated customer credit
        var pay1 = paymentService.recordPayment(owner, new PaymentService.RecordPaymentRequest(
                PaymentType.CUSTOMER_RECEIPT, customerId, LocalDate.now(), PaymentMethod.BANK_TRANSFER,
                bank.getId(), "IDR", null, new BigDecimal("9500000"), "PAY-OVER", null, "idem-key-100",
                List.of(new PaymentService.AllocationInput(invExcl.id(), null, new BigDecimal("8991000")))
        ), null);
        assertThat(pay1.unallocatedAmount()).isEqualTo("509000.00");

        // Duplicate idempotency key returns identical payment without double posting
        var payDup = paymentService.recordPayment(owner, new PaymentService.RecordPaymentRequest(
                PaymentType.CUSTOMER_RECEIPT, customerId, LocalDate.now(), PaymentMethod.BANK_TRANSFER,
                bank.getId(), "IDR", null, new BigDecimal("9500000"), "PAY-OVER", null, "idem-key-100",
                List.of(new PaymentService.AllocationInput(invExcl.id(), null, new BigDecimal("8991000")))
        ), null);
        assertThat(payDup.id()).isEqualTo(pay1.id());
        assertThat(balanceOf(accountService.listAccounts(org.getId(), LocalDate.now(), false), custCredit.getId())).isEqualTo("509000.00");

        // Tax-inclusive pricing test
        var invIncl = invoiceService.createDraft(owner, new InvoiceService.CreateInvoiceRequest(
                customerId, LocalDate.now(), LocalDate.now().plusDays(15), 15, "IDR", null, true,
                null, null, true,
                List.of(new InvoiceService.InvoiceLineRequest(null, "Tax-inclusive package", rev.getId(), BigDecimal.ONE, new BigDecimal("11100000"), BigDecimal.ZERO, null, new BigDecimal("11")))
        ));
        assertThat(invIncl.totalAmount()).isEqualTo("11100000.00");
        assertThat(invIncl.taxTotal()).isEqualTo("1100000.00");

        // Verify valid A4 PDF generation
        byte[] pdfBytes = pdfService.renderInvoicePdf(invoiceService.requireInvoiceEntity(org.getId(), invIncl.id()));
        assertThat(pdfBytes.length).isGreaterThan(1000);
        assertThat(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void concurrentInvoiceIssuanceGeneratesUniqueSequentialNumbers() throws Exception {
        Account rev = accountService.requireBySystemCode(org.getId(), "SERVICE_REVENUE");
        int threads = 6;
        List<UUID> draftIds = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            var draft = invoiceService.createDraft(owner, new InvoiceService.CreateInvoiceRequest(
                    customerId, LocalDate.now(), LocalDate.now().plusDays(30), 30, "IDR", null, false,
                    "Concurrent " + i, null, false,
                    List.of(new InvoiceService.InvoiceLineRequest(null, "Service " + i, rev.getId(), BigDecimal.ONE, new BigDecimal("1000000"), BigDecimal.ZERO, null, BigDecimal.ZERO))
            ));
            draftIds.add(draft.id());
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<String>> tasks = draftIds.stream()
                    .map(id -> (Callable<String>) () -> invoiceService.issueInvoice(owner, id).invoiceNumber())
                    .toList();
            List<Future<String>> futures = pool.invokeAll(tasks);
            Set<String> numbers = new HashSet<>();
            for (Future<String> f : futures) {
                numbers.add(f.get());
            }
            assertThat(numbers).hasSize(threads);
        } finally {
            pool.shutdown();
        }
    }

    private String balanceOf(List<AccountService.AccountDto> list, UUID id) {
        return list.stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow().balance();
    }
}
