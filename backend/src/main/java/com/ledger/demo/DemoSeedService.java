package com.ledger.demo;

import com.ledger.accounting.Account;
import com.ledger.accounting.AccountService;
import com.ledger.approval.ApprovalService;
import com.ledger.banking.BankingService;
import com.ledger.budget.BudgetService;
import com.ledger.contact.ContactService;
import com.ledger.contact.ContactType;
import com.ledger.expense.ExpenseService;
import com.ledger.expense.ExpenseType;
import com.ledger.identity.User;
import com.ledger.identity.UserRepository;
import com.ledger.invoice.InvoiceService;
import com.ledger.journal.JournalService;
import com.ledger.organization.OrgRole;
import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationMembership;
import com.ledger.organization.OrganizationMembershipRepository;
import com.ledger.organization.OrganizationRepository;
import com.ledger.organization.OrganizationService;
import com.ledger.payment.PaymentMethod;
import com.ledger.payment.PaymentService;
import com.ledger.payment.PaymentType;
import com.ledger.reconciliation.ReconciliationService;
import com.ledger.shared.LedgerPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Seeds an isolated synthetic 6-month dataset for a fictional digital agency (Nusa Karsa Studio)
 * exclusively through the validated accounting, invoicing, payment, expense, banking, and budget services.
 */
@Service
public class DemoSeedService {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedService.class);
    public static final String DEMO_OWNER_EMAIL = "demo@nusakarsa.demo";
    public static final String DEMO_OFFICER_EMAIL = "finance@nusakarsa.demo";

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationService organizationService;
    private final AccountService accountService;
    private final ContactService contactService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final ExpenseService expenseService;
    private final JournalService journalService;
    private final BankingService bankingService;
    private final ReconciliationService reconciliationService;
    private final BudgetService budgetService;
    private final ApprovalService approvalService;
    private final PasswordEncoder passwordEncoder;
    private final boolean autoSeed;

    public DemoSeedService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            OrganizationService organizationService,
            AccountService accountService,
            ContactService contactService,
            InvoiceService invoiceService,
            PaymentService paymentService,
            ExpenseService expenseService,
            JournalService journalService,
            BankingService bankingService,
            ReconciliationService reconciliationService,
            BudgetService budgetService,
            ApprovalService approvalService,
            PasswordEncoder passwordEncoder,
            @Value("${ledger.demo.auto-seed:true}") boolean autoSeed) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.organizationService = organizationService;
        this.accountService = accountService;
        this.contactService = contactService;
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
        this.expenseService = expenseService;
        this.journalService = journalService;
        this.bankingService = bankingService;
        this.reconciliationService = reconciliationService;
        this.budgetService = budgetService;
        this.approvalService = approvalService;
        this.passwordEncoder = passwordEncoder;
        this.autoSeed = autoSeed;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (autoSeed) {
            try {
                ensureDemoWorkspace();
            } catch (Exception ex) {
                log.warn("Demo auto-seed skipped or failed: {}", ex.getMessage());
            }
        }
    }

    @Transactional
    public DemoHandle ensureDemoWorkspace() {
        User demoOwner = userRepository.findByEmailIgnoreCase(DEMO_OWNER_EMAIL).orElse(null);
        Organization demoOrg = organizationRepository.findFirstByDemoTrueOrderByCreatedAtAsc().orElse(null);
        if (demoOwner != null && demoOrg != null) {
            return new DemoHandle(demoOwner, demoOrg);
        }

        if (demoOwner == null) {
            demoOwner = userRepository.save(new User(
                    DEMO_OWNER_EMAIL,
                    passwordEncoder.encode("DemoWorkspace#2026"),
                    "Raka Pratama (Demo)",
                    true
            ));
        }

        User demoOfficer = userRepository.findByEmailIgnoreCase(DEMO_OFFICER_EMAIL)
                .orElseGet(() -> userRepository.save(new User(
                        DEMO_OFFICER_EMAIL,
                        passwordEncoder.encode("DemoOfficer#2026"),
                        "Sinta Lestari (Demo)",
                        true
                )));

        demoOrg = organizationService.createOrganizationInternal(
                demoOwner,
                "Nusa Karsa Studio",
                "Digital Product & Engineering Agency",
                "IDR",
                "Asia/Jakarta",
                "id-ID",
                1,
                true
        );
        demoOrg.setContactEmail("billing@nusakarsa.example");
        demoOrg.setContactPhone("+62 21 5550 1840");
        demoOrg.setAddress("Jl. Kemang Raya No. 48, Jakarta Selatan 12730, Indonesia");
        demoOrg.setTaxId("09.254.294.3-012.000");
        demoOrg.setOnboardingCompleted(true);
        organizationRepository.save(demoOrg);

        membershipRepository.save(new OrganizationMembership(demoOrg.getId(), demoOfficer.getId(), OrgRole.FINANCE_OFFICER));

        LedgerPrincipal ownerPrincipal = new LedgerPrincipal(
                demoOwner.getId(), demoOwner.getEmail(), demoOwner.getFullName(), true,
                UUID.randomUUID(), demoOrg.getId(), OrgRole.OWNER, true
        );
        LedgerPrincipal officerPrincipal = new LedgerPrincipal(
                demoOfficer.getId(), demoOfficer.getEmail(), demoOfficer.getFullName(), true,
                UUID.randomUUID(), demoOrg.getId(), OrgRole.FINANCE_OFFICER, true
        );

        seedTransactions(ownerPrincipal, officerPrincipal, demoOrg);
        log.info("Seeded synthetic demonstration workspace: {} ({})", demoOrg.getName(), demoOrg.getId());
        return new DemoHandle(demoOwner, demoOrg);
    }

    private void seedTransactions(LedgerPrincipal owner, LedgerPrincipal officer, Organization org) {
        UUID orgId = org.getId();
        LocalDate today = LocalDate.now();
        YearMonth currentYm = YearMonth.from(today);
        YearMonth m5 = currentYm.minusMonths(5);
        YearMonth m4 = currentYm.minusMonths(4);
        YearMonth m3 = currentYm.minusMonths(3);
        YearMonth m2 = currentYm.minusMonths(2);
        YearMonth m1 = currentYm.minusMonths(1);
        YearMonth m0 = currentYm;

        // Configure org settings
        organizationService.updateOrganization(owner, new OrganizationService.UpdateOrganizationRequest(
                null, null, null, null, null, null, null, null, true,
                "INV", "VAT", new BigDecimal("11"), false, 30,
                true, false, false, true, new BigDecimal("15000000"),
                "Bank Transfer: Sample Demo Clearing Reference (Demonstration Data Only)",
                "Thank you for working with Nusa Karsa Studio. Figures shown are synthetic demonstration data."
        ));

        // Manual USD/IDR rates to demonstrate multi-currency invoicing and realized FX differences
        organizationService.upsertExchangeRate(owner, new OrganizationService.UpsertExchangeRateRequest(
                "USD", "IDR", new BigDecimal("15800"), m3.atDay(1)
        ));
        organizationService.upsertExchangeRate(owner, new OrganizationService.UpsertExchangeRateRequest(
                "USD", "IDR", new BigDecimal("16050"), m2.atDay(1)
        ));

        Account bankAcc = accountService.requireBySystemCode(orgId, "BANK");
        Account cashAcc = accountService.requireBySystemCode(orgId, "CASH");
        Account equipmentAcc = accountService.requireBySystemCode(orgId, "EQUIPMENT");
        Account serviceRev = accountService.requireBySystemCode(orgId, "SERVICE_REVENUE");
        Account retainerRev = accountService.requireBySystemCode(orgId, "RETAINER_REVENUE");
        Account softwareExp = accountService.requireBySystemCode(orgId, "SOFTWARE_EXPENSE");
        Account contractorExp = accountService.requireBySystemCode(orgId, "CONTRACTOR_EXPENSE");
        Account marketingExp = accountService.requireBySystemCode(orgId, "MARKETING_EXPENSE");
        Account officeExp = accountService.requireBySystemCode(orgId, "OFFICE_EXPENSE");
        Account rentExp = accountService.requireBySystemCode(orgId, "RENT_EXPENSE");

        // Primary operating bank account with opening balance (posts balanced opening journal)
        BankingService.BankAccountDto mainBank = bankingService.createBankAccount(owner, new BankingService.CreateBankAccountRequest(
                "Operating Bank Account (IDR)", "Bank Mandiri Demo", "**** 4821", "IDR",
                bankAcc.getId(), new BigDecimal("85000000"), m5.atDay(1)
        ));
        bankingService.createBankAccount(owner, new BankingService.CreateBankAccountRequest(
                "Studio Petty Cash", "Cash Vault", "CASH-01", "IDR",
                cashAcc.getId(), new BigDecimal("5000000"), m5.atDay(1)
        ));

        // Equipment purchase (Investing cash outflow)
        journalService.createManualJournal(owner, new JournalService.CreateJournalRequest(
                m5.atDay(8), "Purchase of two studio workstations", "EQP-2026-01", "IDR", null, true, false,
                List.of(
                        JournalService.JournalLineInput.debit(equipmentAcc.getId(), null, "Studio workstations", new BigDecimal("28000000")),
                        JournalService.JournalLineInput.credit(bankAcc.getId(), null, "Bank disbursement for equipment", new BigDecimal("28000000"))
                )
        ));

        // Contacts: Customers and Suppliers
        var cakrawala = contactService.createContact(owner, new ContactService.CreateContactRequest(
                ContactType.CUSTOMER, "PT Cakrawala Media", "Dimas Hendra", "finance@cakrawala.example",
                "+62 21 5550 2201", "Jl. Sudirman Kav 52, Jakarta", null, "IDR", 30, "Enterprise web retainer client"
        ));
        var sinar = contactService.createContact(owner, new ContactService.CreateContactRequest(
                ContactType.CUSTOMER, "PT Sinar Nusantara Logistik", "Nadia Kusuma", "ap@sinarnusantara.example",
                "+62 21 5550 3309", "Jl. Gatot Subroto No. 19, Jakarta", null, "IDR", 30, "Logistics portal redesign"
        ));
        var koperasi = contactService.createContact(owner, new ContactService.CreateContactRequest(
                ContactType.CUSTOMER, "Koperasi Mandiri Raya", "Budi Santoso", "bendahara@mandiri-koperasi.example",
                "+62 22 5550 1120", "Jl. Dago No. 88, Bandung", null, "IDR", 14, "Member platform implementation"
        ));
        var harborview = contactService.createContact(owner, new ContactService.CreateContactRequest(
                ContactType.CUSTOMER, "Harborview Digital Pte Ltd", "Claire Tan", "accounts@harborview.example",
                "+65 6550 8820", "10 Anson Road, Singapore", null, "USD", 30, "Cross-border advisory client (USD)"
        ));

        var awanCloud = contactService.createContact(owner, new ContactService.CreateContactRequest(
                ContactType.SUPPLIER, "Awan Cloud Infrastructure", "Billing Desk", "billing@awancloud.example",
                "+62 21 5550 7700", "Jakarta Selatan", null, "IDR", 15, "Hosting and cloud compute"
        ));
        var karyaGrafika = contactService.createContact(owner, new ContactService.CreateContactRequest(
                ContactType.SUPPLIER, "CV Karya Grafika", "Hendra Wijaya", "orders@karyagrafika.example",
                "+62 21 5550 6612", "Jakarta Barat", null, "IDR", 30, "Design production and print"
        ));
        var ruangKerja = contactService.createContact(owner, new ContactService.CreateContactRequest(
                ContactType.SUPPLIER, "Ruang Kerja Kemang", "Property Admin", "lease@ruangkerja.example",
                "+62 21 5550 4410", "Kemang, Jakarta Selatan", null, "IDR", 15, "Monthly studio space lease"
        ));

        // Service Catalog
        var catDesign = contactService.createCatalogItem(owner, new ContactService.CreateCatalogItemRequest(
                "SVC-DES", "Product Interface Design", "End-to-end UX architecture and visual design system",
                new BigDecimal("18000000"), "IDR", serviceRev.getId(), new BigDecimal("11")
        ));
        var catEng = contactService.createCatalogItem(owner, new ContactService.CreateCatalogItemRequest(
                "SVC-ENG", "Web Application Engineering", "Full-stack TypeScript and Java engineering sprint",
                new BigDecimal("25000000"), "IDR", serviceRev.getId(), new BigDecimal("11")
        ));
        var catRetainer = contactService.createCatalogItem(owner, new ContactService.CreateCatalogItemRequest(
                "SVC-RET", "Monthly Engineering Retainer", "Dedicated maintenance, performance, and roadmap support",
                new BigDecimal("15000000"), "IDR", retainerRev.getId(), new BigDecimal("11")
        ));
        var catAudit = contactService.createCatalogItem(owner, new ContactService.CreateCatalogItemRequest(
                "SVC-AUD", "Technical Architecture Review", "Cross-border systems audit billed in USD",
                new BigDecimal("2500"), "USD", serviceRev.getId(), BigDecimal.ZERO
        ));

        // --- Month -5 ---
        var inv1 = invoiceService.createDraft(owner, new InvoiceService.CreateInvoiceRequest(
                cakrawala.id(), m5.atDay(5), m5.atDay(28), 23, "IDR", null, false,
                "Phase 1 Discovery & Architecture", null, true,
                List.of(new InvoiceService.InvoiceLineRequest(catEng.id(), "Phase 1 Web Architecture Sprint", serviceRev.getId(), BigDecimal.ONE, new BigDecimal("25000000"), BigDecimal.ZERO, null, new BigDecimal("11")))
        ));
        paymentService.recordPayment(owner, new PaymentService.RecordPaymentRequest(
                PaymentType.CUSTOMER_RECEIPT, cakrawala.id(), m5.atDay(22), PaymentMethod.BANK_TRANSFER,
                bankAcc.getId(), "IDR", null, new BigDecimal(inv1.totalAmount()), "TRF-CKM-M5", "Full settlement", "demo-pay-1",
                List.of(new PaymentService.AllocationInput(inv1.id(), null, new BigDecimal(inv1.totalAmount())))
        ), null);

        seedMonthlyOperatingCosts(owner, m5, bankAcc.getId(), rentExp.getId(), softwareExp.getId(), officeExp.getId(), ruangKerja.id(), awanCloud.id(), false);

        // --- Month -4 ---
        var inv2 = invoiceService.createDraft(owner, new InvoiceService.CreateInvoiceRequest(
                sinar.id(), m4.atDay(6), m4.atDay(28), 22, "IDR", null, false,
                "Logistics Portal UX System", null, true,
                List.of(new InvoiceService.InvoiceLineRequest(catDesign.id(), "Product Interface Design", serviceRev.getId(), BigDecimal.ONE, new BigDecimal("22000000"), new BigDecimal("5"), null, new BigDecimal("11")))
        ));
        paymentService.recordPayment(owner, new PaymentService.RecordPaymentRequest(
                PaymentType.CUSTOMER_RECEIPT, sinar.id(), m4.atDay(25), PaymentMethod.BANK_TRANSFER,
                bankAcc.getId(), "IDR", null, new BigDecimal(inv2.totalAmount()), "TRF-SNR-M4", "Full settlement", "demo-pay-2",
                List.of(new PaymentService.AllocationInput(inv2.id(), null, new BigDecimal(inv2.totalAmount())))
        ), null);

        var inv3 = invoiceService.createDraft(owner, new InvoiceService.CreateInvoiceRequest(
                cakrawala.id(), m4.atDay(10), m3.atDay(10), 30, "IDR", null, false,
                "Monthly Retainer", null, true,
                List.of(new InvoiceService.InvoiceLineRequest(catRetainer.id(), "Monthly Engineering Retainer", retainerRev.getId(), BigDecimal.ONE, new BigDecimal("15000000"), BigDecimal.ZERO, null, new BigDecimal("11")))
        ));
        paymentService.recordPayment(owner, new PaymentService.RecordPaymentRequest(
                PaymentType.CUSTOMER_RECEIPT, cakrawala.id(), m3.atDay(5), PaymentMethod.BANK_TRANSFER,
                bankAcc.getId(), "IDR", null, new BigDecimal(inv3.totalAmount()), "TRF-CKM-M4", "Full settlement", "demo-pay-3",
                List.of(new PaymentService.AllocationInput(inv3.id(), null, new BigDecimal(inv3.totalAmount())))
        ), null);

        seedMonthlyOperatingCosts(owner, m4, bankAcc.getId(), rentExp.getId(), softwareExp.getId(), officeExp.getId(), ruangKerja.id(), awanCloud.id(), false);

        // --- Month -3 (Includes Multi-Currency USD invoice + Realized FX gain on settlement in Month -2) ---
        var invUsd = invoiceService.createDraft(owner, new InvoiceService.CreateInvoiceRequest(
                harborview.id(), m3.atDay(8), m2.atDay(8), 31, "USD", new BigDecimal("15800"), false,
                "Architecture Audit - Singapore Engagement", null, true,
                List.of(new InvoiceService.InvoiceLineRequest(catAudit.id(), "Technical Architecture Review", serviceRev.getId(), new BigDecimal("2"), new BigDecimal("2500"), BigDecimal.ZERO, null, BigDecimal.ZERO))
        ));
        paymentService.recordPayment(owner, new PaymentService.RecordPaymentRequest(
                PaymentType.CUSTOMER_RECEIPT, harborview.id(), m2.atDay(4), PaymentMethod.BANK_TRANSFER,
                bankAcc.getId(), "USD", new BigDecimal("16050"), new BigDecimal("5000"), "WIRE-HBV-USD", "Settled in USD at 16,050 IDR/USD", "demo-pay-usd",
                List.of(new PaymentService.AllocationInput(invUsd.id(), null, new BigDecimal("5000")))
        ), null);

        var inv4 = invoiceService.createDraft(owner, new InvoiceService.CreateInvoiceRequest(
                koperasi.id(), m3.atDay(14), m3.atDay(28), 14, "IDR", null, false,
                "Member Portal Foundation", null, true,
                List.of(new InvoiceService.InvoiceLineRequest(catEng.id(), "Member Portal Core Build", serviceRev.getId(), BigDecimal.ONE, new BigDecimal("19500000"), BigDecimal.ZERO, null, BigDecimal.ZERO))
        ));
        paymentService.recordPayment(owner, new PaymentService.RecordPaymentRequest(
                PaymentType.CUSTOMER_RECEIPT, koperasi.id(), m3.atDay(26), PaymentMethod.BANK_TRANSFER,
                bankAcc.getId(), "IDR", null, new BigDecimal("19500000"), "TRF-KMR-M3", "Paid in full", "demo-pay-4",
                List.of(new PaymentService.AllocationInput(inv4.id(), null, new BigDecimal("19500000")))
        ), null);

        seedMonthlyOperatingCosts(owner, m3, bankAcc.getId(), rentExp.getId(), softwareExp.getId(), officeExp.getId(), ruangKerja.id(), awanCloud.id(), false);

        // --- Month -2 (Includes supplier bill paid + contractor expense) ---
        var inv5 = invoiceService.createDraft(owner, new InvoiceService.CreateInvoiceRequest(
                cakrawala.id(), m2.atDay(5), m1.atDay(5), 30, "IDR", null, false,
                "Retainer + Analytics Module", null, true,
                List.of(
                        new InvoiceService.InvoiceLineRequest(catRetainer.id(), "Monthly Engineering Retainer", retainerRev.getId(), BigDecimal.ONE, new BigDecimal("15000000"), BigDecimal.ZERO, null, new BigDecimal("11")),
                        new InvoiceService.InvoiceLineRequest(catEng.id(), "Reporting Module Enhancement", serviceRev.getId(), BigDecimal.ONE, new BigDecimal("12000000"), BigDecimal.ZERO, null, new BigDecimal("11"))
                )
        ));
        paymentService.recordPayment(owner, new PaymentService.RecordPaymentRequest(
                PaymentType.CUSTOMER_RECEIPT, cakrawala.id(), m1.atDay(2), PaymentMethod.BANK_TRANSFER,
                bankAcc.getId(), "IDR", null, new BigDecimal(inv5.totalAmount()), "TRF-CKM-M2", "Paid in full", "demo-pay-5",
                List.of(new PaymentService.AllocationInput(inv5.id(), null, new BigDecimal(inv5.totalAmount())))
        ), null);

        var bill1 = expenseService.createExpense(owner, new ExpenseService.CreateExpenseRequest(
                ExpenseType.SUPPLIER_BILL, "Brand Collateral & Production Print", m2.atDay(12), m1.atDay(12),
                karyaGrafika.id(), null, marketingExp.getId(), null, "IDR", null,
                new BigDecimal("6500000"), BigDecimal.ZERO, null, "Supplier invoice #KG-402", true
        ));
        paymentService.recordPayment(owner, new PaymentService.RecordPaymentRequest(
                PaymentType.SUPPLIER_PAYMENT, karyaGrafika.id(), m1.atDay(6), PaymentMethod.BANK_TRANSFER,
                bankAcc.getId(), "IDR", null, new BigDecimal("6500000"), "PAY-KYG-M2", "Settled supplier bill", "demo-spay-1",
                List.of(new PaymentService.AllocationInput(null, bill1.id(), new BigDecimal("6500000")))
        ), null);

        seedMonthlyOperatingCosts(owner, m2, bankAcc.getId(), rentExp.getId(), softwareExp.getId(), officeExp.getId(), ruangKerja.id(), awanCloud.id(), false);

        // --- Month -1 (Includes Overdue invoice + Partially Paid invoice + Credit Note + Open Supplier Bill) ---
        invoiceService.createDraft(owner, new InvoiceService.CreateInvoiceRequest(
                koperasi.id(), m1.atDay(4), m1.atDay(18), 14, "IDR", null, false,
                "Phase 2 Cooperative Reporting Module (Overdue sample)", null, true,
                List.of(new InvoiceService.InvoiceLineRequest(catEng.id(), "Phase 2 Reporting Sprint", serviceRev.getId(), BigDecimal.ONE, new BigDecimal("14000000"), BigDecimal.ZERO, null, BigDecimal.ZERO))
        ));

        var invPartial = invoiceService.createDraft(owner, new InvoiceService.CreateInvoiceRequest(
                sinar.id(), m1.atDay(12), m0.atDay(Math.min(12, m0.lengthOfMonth())), 30, "IDR", null, false,
                "Warehouse Dispatch Dashboard", null, true,
                List.of(new InvoiceService.InvoiceLineRequest(catEng.id(), "Dispatch Platform Delivery", serviceRev.getId(), BigDecimal.ONE, new BigDecimal("30000000"), BigDecimal.ZERO, null, BigDecimal.ZERO))
        ));
        paymentService.recordPayment(owner, new PaymentService.RecordPaymentRequest(
                PaymentType.CUSTOMER_RECEIPT, sinar.id(), m1.atDay(24), PaymentMethod.BANK_TRANSFER,
                bankAcc.getId(), "IDR", null, new BigDecimal("18000000"), "TRF-SNR-PARTIAL", "60% milestone receipt", "demo-pay-partial",
                List.of(new PaymentService.AllocationInput(invPartial.id(), null, new BigDecimal("18000000")))
        ), null);
        invoiceService.issueCreditNote(owner, invPartial.id(), new InvoiceService.CreateCreditNoteRequest(
                m1.atDay(26), "Scope reduction on legacy CSV export", new BigDecimal("2000000"), BigDecimal.ZERO
        ));

        // Open supplier bill (partially paid)
        var billOpen = expenseService.createExpense(owner, new ExpenseService.CreateExpenseRequest(
                ExpenseType.SUPPLIER_BILL, "Q4 Production Design Retainer", m1.atDay(18), m0.atDay(Math.min(18, m0.lengthOfMonth())),
                karyaGrafika.id(), null, contractorExp.getId(), null, "IDR", null,
                new BigDecimal("9000000"), BigDecimal.ZERO, null, "Contract milestone #2", true
        ));
        paymentService.recordPayment(owner, new PaymentService.RecordPaymentRequest(
                PaymentType.SUPPLIER_PAYMENT, karyaGrafika.id(), m0.atDay(Math.min(5, today.getDayOfMonth())), PaymentMethod.BANK_TRANSFER,
                bankAcc.getId(), "IDR", null, new BigDecimal("4000000"), "PAY-KYG-PARTIAL", "Partial settlement", "demo-spay-2",
                List.of(new PaymentService.AllocationInput(null, billOpen.id(), new BigDecimal("4000000")))
        ), null);

        seedMonthlyOperatingCosts(owner, m1, bankAcc.getId(), rentExp.getId(), softwareExp.getId(), officeExp.getId(), ruangKerja.id(), awanCloud.id(), false);

        // --- Current Month ---
        LocalDate invCurrDate = m0.atDay(Math.min(3, today.getDayOfMonth()));
        var invCurrent = invoiceService.createDraft(owner, new InvoiceService.CreateInvoiceRequest(
                cakrawala.id(), invCurrDate, today.plusDays(14), 14, "IDR", null, false,
                "Monthly Engineering Retainer", null, true,
                List.of(new InvoiceService.InvoiceLineRequest(catRetainer.id(), "Monthly Engineering Retainer", retainerRev.getId(), BigDecimal.ONE, new BigDecimal("18500000"), BigDecimal.ZERO, null, BigDecimal.ZERO))
        ));
        var payCurrent = paymentService.recordPayment(owner, new PaymentService.RecordPaymentRequest(
                PaymentType.CUSTOMER_RECEIPT, cakrawala.id(), today.minusDays(Math.min(2, today.getDayOfMonth() - 1)), PaymentMethod.BANK_TRANSFER,
                bankAcc.getId(), "IDR", null, new BigDecimal("18500000"), "TRF-CKM-CURR", "Retainer receipt", "demo-pay-curr",
                List.of(new PaymentService.AllocationInput(invCurrent.id(), null, new BigDecimal("18500000")))
        ), null);

        // Open current invoice (upcoming due date)
        invoiceService.createDraft(owner, new InvoiceService.CreateInvoiceRequest(
                harborview.id(), invCurrDate, today.plusDays(21), 21, "USD", new BigDecimal("16050"), false,
                "Cloud Scalability Advisory", null, true,
                List.of(new InvoiceService.InvoiceLineRequest(catAudit.id(), "Cloud Architecture Advisory", serviceRev.getId(), BigDecimal.ONE, new BigDecimal("3200"), BigDecimal.ZERO, null, BigDecimal.ZERO))
        ));

        // Draft invoice ready to inspect or issue
        invoiceService.createDraft(owner, new InvoiceService.CreateInvoiceRequest(
                sinar.id(), today, today.plusDays(30), 30, "IDR", null, false,
                "Draft proposal invoice for Q1 mobile companion", null, false,
                List.of(new InvoiceService.InvoiceLineRequest(catDesign.id(), "Mobile Companion Design Sprint", serviceRev.getId(), BigDecimal.ONE, new BigDecimal("16000000"), BigDecimal.ZERO, null, new BigDecimal("11")))
        ));

        // Current month immediate expense + unpaid supplier bill
        var expCloudCurr = expenseService.createExpense(owner, new ExpenseService.CreateExpenseRequest(
                ExpenseType.IMMEDIATE_EXPENSE, "Cloud Infrastructure & Staging Cluster", invCurrDate, null,
                awanCloud.id(), null, softwareExp.getId(), bankAcc.getId(), "IDR", null,
                new BigDecimal("2450000"), BigDecimal.ZERO, null, "Monthly cloud usage", true
        ));

        expenseService.createExpense(owner, new ExpenseService.CreateExpenseRequest(
                ExpenseType.SUPPLIER_BILL, "Studio Workspace Lease", invCurrDate, today.plusDays(10),
                ruangKerja.id(), null, rentExp.getId(), null, "IDR", null,
                new BigDecimal("8500000"), BigDecimal.ZERO, null, "Current month lease invoice", true
        ));

        // Expense above approval threshold submitted by demo Finance Officer (awaiting Demo Owner approval)
        var pendingExp = expenseService.createExpense(officer, new ExpenseService.CreateExpenseRequest(
                ExpenseType.SUPPLIER_BILL, "Annual Enterprise Design & CI Tooling License", today, today.plusDays(15),
                awanCloud.id(), null, softwareExp.getId(), null, "IDR", null,
                new BigDecimal("18500000"), BigDecimal.ZERO, null, "Annual renewal awaiting management approval", false
        ));
        approvalService.submitForApproval(officer, "SUPPLIER_BILL", pendingExp.id());

        // Import realistic bank statement lines matching recent posted bank transactions + 1 unmatched bank fee
        LocalDate payDateCurr = today.minusDays(Math.min(2, today.getDayOfMonth() - 1));
        String csv = String.join("\n",
                "Date,Description,Reference,Debit,Credit",
                m1.atDay(24) + ",Transfer from PT Sinar Nusantara Milestone,TRF-SNR-PARTIAL,,18000000",
                m1.atDay(6) + ",Payment to CV Karya Grafika,PAY-KYG-M2,6500000,",
                invCurrDate + ",Awan Cloud Infrastructure Hosting,CLOUD-CURR,2450000,",
                payDateCurr + ",Transfer from PT Cakrawala Media Retainer,TRF-CKM-CURR,,18500000",
                today + ",Monthly Bank Account Maintenance Fee,ADM-FEE-CURR,75000,"
        );
        bankingService.commitCsvImport(owner, mainBank.id(), new BankingService.CsvImportRequest(
                "nusa-karsa-bank-statement.csv", csv, "Date", "Description", "Reference", "Debit", "Credit", null, "yyyy-MM-dd"
        ));

        // Reconcile the first 2 suggested matches automatically so the workspace displays both matched history and pending matches
        var reconWs = reconciliationService.getWorkspace(orgId, mainBank.id());
        int matched = 0;
        for (var sug : reconWs.suggestedMatches()) {
            if (matched >= 2) break;
            reconciliationService.confirmMatch(owner, new ReconciliationService.ConfirmMatchRequest(
                    sug.bankStatementLineId(), sug.bestCandidate().journalLineId(), "EXACT", "Matched during demo setup"
            ));
            matched++;
        }

        // 6-Month Operating Budget
        budgetService.createBudget(owner, new BudgetService.CreateBudgetRequest(
                "Six-Month Operating Budget", null, "ANNUAL", m5.atDay(1), m0.atEndOfMonth(),
                "Operating expense allocations for Nusa Karsa Studio",
                List.of(
                        new BudgetService.BudgetLineInput(rentExp.getId(), null, new BigDecimal("54000000"), "Studio space lease"),
                        new BudgetService.BudgetLineInput(softwareExp.getId(), null, new BigDecimal("18000000"), "Cloud & SaaS tooling"),
                        new BudgetService.BudgetLineInput(contractorExp.getId(), null, new BigDecimal("20000000"), "External specialists"),
                        new BudgetService.BudgetLineInput(marketingExp.getId(), null, new BigDecimal("12000000"), "Brand collateral"),
                        new BudgetService.BudgetLineInput(officeExp.getId(), null, new BigDecimal("8000000"), "Studio supplies & utilities")
                )
        ));
    }

    private void seedMonthlyOperatingCosts(
            LedgerPrincipal owner, YearMonth ym, UUID bankAccountId,
            UUID rentAccId, UUID softwareAccId, UUID officeAccId,
            UUID landlordId, UUID cloudId, boolean asBills
    ) {
        expenseService.createExpense(owner, new ExpenseService.CreateExpenseRequest(
                ExpenseType.IMMEDIATE_EXPENSE, "Studio Lease - " + ym, ym.atDay(3), null,
                landlordId, null, rentAccId, bankAccountId, "IDR", null,
                new BigDecimal("8500000"), BigDecimal.ZERO, null, "Monthly studio rent", true
        ));
        expenseService.createExpense(owner, new ExpenseService.CreateExpenseRequest(
                ExpenseType.IMMEDIATE_EXPENSE, "Cloud Hosting & SaaS - " + ym, ym.atDay(11), null,
                cloudId, null, softwareAccId, bankAccountId, "IDR", null,
                new BigDecimal("2350000"), BigDecimal.ZERO, null, "Infrastructure subscription", true
        ));
        expenseService.createExpense(owner, new ExpenseService.CreateExpenseRequest(
                ExpenseType.IMMEDIATE_EXPENSE, "Office Utilities & Internet - " + ym, ym.atDay(19), null,
                null, null, officeAccId, bankAccountId, "IDR", null,
                new BigDecimal("1150000"), BigDecimal.ZERO, null, "Fiber internet & electricity", true
        ));
    }

    public record DemoHandle(User demoUser, Organization demoOrganization) {}
}
