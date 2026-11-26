package com.ledger.invoice;

import com.ledger.accounting.Account;
import com.ledger.accounting.AccountService;
import com.ledger.accounting.AccountType;
import com.ledger.audit.AuditService;
import com.ledger.contact.Contact;
import com.ledger.contact.ContactService;
import com.ledger.journal.JournalEntry;
import com.ledger.journal.JournalService;
import com.ledger.journal.JournalSourceType;
import com.ledger.organization.FinancialSettings;
import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationService;
import com.ledger.shared.DocumentSequenceService;
import com.ledger.shared.DomainEvents;
import com.ledger.shared.LedgerException;
import com.ledger.shared.LedgerPrincipal;
import com.ledger.shared.MoneyUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final ContactService contactService;
    private final AccountService accountService;
    private final OrganizationService organizationService;
    private final JournalService journalService;
    private final DocumentSequenceService sequenceService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            CreditNoteRepository creditNoteRepository,
            ContactService contactService,
            AccountService accountService,
            OrganizationService organizationService,
            JournalService journalService,
            DocumentSequenceService sequenceService,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher) {
        this.invoiceRepository = invoiceRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.contactService = contactService;
        this.accountService = accountService;
        this.organizationService = organizationService;
        this.journalService = journalService;
        this.sequenceService = sequenceService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InvoiceDto createDraft(LedgerPrincipal principal, CreateInvoiceRequest req) {
        principal.requireFinanceOperations();
        UUID orgId = principal.requireOrganizationId();
        Organization org = organizationService.getOrganizationEntity(orgId);
        FinancialSettings settings = organizationService.getSettingsEntity(orgId);

        Contact customer = contactService.requireContact(orgId, req.customerId());
        if (!customer.isCustomer()) {
            throw LedgerException.businessRule("CONTACT_NOT_CUSTOMER", "Selected contact is not a customer");
        }

        LocalDate issueDate = req.issueDate() != null ? req.issueDate() : LocalDate.now();
        int terms = req.paymentTermsDays() != null ? req.paymentTermsDays() : customer.getPaymentTermsDays();
        LocalDate dueDate = req.dueDate() != null ? req.dueDate() : issueDate.plusDays(terms);
        if (dueDate.isBefore(issueDate)) {
            throw LedgerException.badRequest("INVALID_DUE_DATE", "Due date cannot precede issue date");
        }

        String ccy = (req.currencyCode() == null || req.currencyCode().isBlank())
                ? customer.getCurrencyCode() : req.currencyCode().toUpperCase(Locale.ROOT);
        BigDecimal fxRate = organizationService.resolveExchangeRate(orgId, ccy, issueDate, req.exchangeRate());
        boolean taxInclusive = req.taxInclusive() != null ? req.taxInclusive() : settings.isTaxInclusiveDefault();

        String draftNumber = "DRAFT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String paymentInstructions = req.paymentInstructions() != null
                ? req.paymentInstructions() : settings.getPaymentInstructions();

        Invoice invoice = new Invoice(
                orgId, draftNumber, customer.getId(), issueDate, dueDate, terms,
                ccy, fxRate, taxInclusive, req.notes(), paymentInstructions, principal.userId()
        );
        computeAndAttachLines(invoice, org, req.lines());
        invoiceRepository.save(invoice);

        auditService.record(orgId, "INVOICE_DRAFT_CREATED", "INVOICE", invoice.getId().toString(),
                "Created draft invoice for " + customer.getName(),
                Map.of("total", MoneyUtil.toMoneyString(invoice.getTotalAmount()), "currency", ccy));

        if (Boolean.TRUE.equals(req.issueImmediately())) {
            return issueInvoice(principal, invoice.getId());
        }
        return toDto(invoice, customer.getName());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InvoiceDto updateDraft(LedgerPrincipal principal, UUID invoiceId, UpdateInvoiceRequest req) {
        principal.requireFinanceOperations();
        UUID orgId = principal.requireOrganizationId();
        Invoice invoice = invoiceRepository.findByIdAndOrganizationIdForUpdate(invoiceId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Invoice not found"));

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw LedgerException.businessRule(
                    "INVOICE_IMMUTABLE",
                    "Issued, paid, or voided invoices cannot be edited directly. Use a credit note or reversal."
            );
        }

        Organization org = organizationService.getOrganizationEntity(orgId);
        if (req.customerId() != null) {
            Contact customer = contactService.requireContact(orgId, req.customerId());
            if (!customer.isCustomer()) {
                throw LedgerException.businessRule("CONTACT_NOT_CUSTOMER", "Selected contact is not a customer");
            }
            invoice.setCustomerId(customer.getId());
        }
        if (req.issueDate() != null) invoice.setIssueDate(req.issueDate());
        if (req.paymentTermsDays() != null) invoice.setPaymentTermsDays(req.paymentTermsDays());
        if (req.dueDate() != null) invoice.setDueDate(req.dueDate());
        if (invoice.getDueDate().isBefore(invoice.getIssueDate())) {
            throw LedgerException.badRequest("INVALID_DUE_DATE", "Due date cannot precede issue date");
        }
        if (req.currencyCode() != null && !req.currencyCode().isBlank()) {
            String ccy = req.currencyCode().toUpperCase(Locale.ROOT);
            invoice.setCurrencyCode(ccy);
            BigDecimal fx = organizationService.resolveExchangeRate(orgId, ccy, invoice.getIssueDate(), req.exchangeRate());
            invoice.setExchangeRate(fx);
        } else if (req.exchangeRate() != null) {
            invoice.setExchangeRate(req.exchangeRate());
        }
        if (req.taxInclusive() != null) invoice.setTaxInclusive(req.taxInclusive());
        if (req.notes() != null) invoice.setNotes(req.notes());
        if (req.paymentInstructions() != null) invoice.setPaymentInstructions(req.paymentInstructions());

        if (req.lines() != null) {
            invoice.clearLines();
            computeAndAttachLines(invoice, org, req.lines());
        }
        invoiceRepository.save(invoice);
        Contact customer = contactService.requireContact(orgId, invoice.getCustomerId());
        return toDto(invoice, customer.getName());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InvoiceDto issueInvoice(LedgerPrincipal principal, UUID invoiceId) {
        principal.requireFinanceOperations();
        UUID orgId = principal.requireOrganizationId();

        Invoice invoice = invoiceRepository.findByIdAndOrganizationIdForUpdate(invoiceId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Invoice not found"));

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw LedgerException.businessRule("INVALID_INVOICE_STATUS", "Only draft invoices can be issued");
        }
        if (invoice.getLines().isEmpty() || !MoneyUtil.isPositive(invoice.getTotalAmount())) {
            throw LedgerException.businessRule("INVALID_INVOICE_TOTAL", "Invoice must contain line items totaling more than zero");
        }

        Organization org = organizationService.getOrganizationEntity(orgId);
        FinancialSettings settings = organizationService.getSettingsEntity(orgId);
        Contact customer = contactService.requireContact(orgId, invoice.getCustomerId());

        String finalNumber = sequenceService.nextNumber(
                orgId, "INVOICE", settings.getInvoicePrefix(), invoice.getIssueDate().getYear()
        );

        Account arAccount = accountService.requireBySystemCode(orgId, "AR");
        Account taxPayableAccount = accountService.requireBySystemCode(orgId, "TAX_PAYABLE");

        // Build balanced journal lines: Debit AR (total), Credit Revenue per account (net), Credit Tax Payable (tax)
        List<JournalService.JournalLineInput> jLines = new ArrayList<>();
        BigDecimal baseTotal = MoneyUtil.convertToBase(invoice.getTotalAmount(), invoice.getExchangeRate(), org.getBaseCurrency());
        jLines.add(JournalService.JournalLineInput.baseDebit(
                arAccount.getId(), customer.getId(),
                "Invoice " + finalNumber + " - " + customer.getName(),
                invoice.getTotalAmount(), baseTotal
        ));

        Map<UUID, BigDecimal> origRevenueByAccount = new LinkedHashMap<>();
        for (InvoiceLine line : invoice.getLines()) {
            origRevenueByAccount.merge(line.getRevenueAccountId(), line.getNetAmount(), BigDecimal::add);
        }

        BigDecimal baseTax = MoneyUtil.isPositive(invoice.getTaxTotal())
                ? MoneyUtil.convertToBase(invoice.getTaxTotal(), invoice.getExchangeRate(), org.getBaseCurrency())
                : MoneyUtil.ZERO;

        BigDecimal sumBaseCredits = baseTax;
        List<Map.Entry<UUID, BigDecimal>> revEntries = new ArrayList<>(origRevenueByAccount.entrySet());
        List<BigDecimal> revBaseAmounts = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : revEntries) {
            BigDecimal baseRev = MoneyUtil.convertToBase(entry.getValue(), invoice.getExchangeRate(), org.getBaseCurrency());
            revBaseAmounts.add(baseRev);
            sumBaseCredits = sumBaseCredits.add(baseRev);
        }

        // Reconcile any minor-unit rounding residual from FX multiplication onto the first revenue line.
        BigDecimal fxResidual = baseTotal.subtract(sumBaseCredits);
        if (fxResidual.compareTo(BigDecimal.ZERO) != 0 && !revBaseAmounts.isEmpty()) {
            revBaseAmounts.set(0, revBaseAmounts.getFirst().add(fxResidual));
        }

        for (int i = 0; i < revEntries.size(); i++) {
            Map.Entry<UUID, BigDecimal> entry = revEntries.get(i);
            BigDecimal baseAmt = revBaseAmounts.get(i);
            if (MoneyUtil.isPositive(baseAmt)) {
                jLines.add(JournalService.JournalLineInput.baseCredit(
                        entry.getKey(), customer.getId(),
                        "Revenue for invoice " + finalNumber,
                        entry.getValue(), baseAmt
                ));
            }
        }

        if (MoneyUtil.isPositive(baseTax)) {
            jLines.add(JournalService.JournalLineInput.baseCredit(
                    taxPayableAccount.getId(), customer.getId(),
                    "Sales tax for invoice " + finalNumber,
                    invoice.getTaxTotal(), baseTax
            ));
        }

        JournalEntry journal = journalService.postAutomatedJournal(
                orgId, principal.userId(), invoice.getIssueDate(),
                "Invoice " + finalNumber + " issued to " + customer.getName(),
                finalNumber, JournalSourceType.INVOICE, invoice.getId(),
                "INVOICE_ISSUE:" + invoice.getId(),
                invoice.getCurrencyCode(), invoice.getExchangeRate(), jLines
        );

        invoice.applyComputedTotals(invoice.getSubtotal(), invoice.getDiscountTotal(), invoice.getTaxTotal(), invoice.getTotalAmount(), baseTotal);
        invoice.markIssued(finalNumber, journal.getId());
        invoiceRepository.save(invoice);

        auditService.record(orgId, "INVOICE_ISSUED", "INVOICE", invoice.getId().toString(),
                "Issued invoice " + finalNumber + " (" + invoice.getCurrencyCode() + " " + MoneyUtil.toMoneyString(invoice.getTotalAmount()) + ")",
                Map.of("journalEntryId", journal.getId().toString(), "customer", customer.getName()));
        eventPublisher.publishEvent(new DomainEvents.InvoiceIssuedEvent(
                orgId, invoice.getId(), finalNumber, customer.getId(),
                invoice.getTotalAmount(), invoice.getCurrencyCode(), invoice.getDueDate()));

        return toDto(invoice, customer.getName());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreditNoteDto issueCreditNote(LedgerPrincipal principal, UUID invoiceId, CreateCreditNoteRequest req) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();

        Invoice invoice = invoiceRepository.findByIdAndOrganizationIdForUpdate(invoiceId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Invoice not found"));
        if (invoice.getStatus() != InvoiceStatus.ISSUED && invoice.getStatus() != InvoiceStatus.PARTIALLY_PAID) {
            throw LedgerException.businessRule("CREDIT_NOTE_NOT_ALLOWED", "Credit notes can only be issued against open invoices");
        }

        Organization org = organizationService.getOrganizationEntity(orgId);
        FinancialSettings settings = organizationService.getSettingsEntity(orgId);
        Contact customer = contactService.requireContact(orgId, invoice.getCustomerId());

        BigDecimal netAmount = MoneyUtil.round(req.netAmount(), invoice.getCurrencyCode());
        BigDecimal taxAmount = req.taxAmount() != null
                ? MoneyUtil.round(req.taxAmount(), invoice.getCurrencyCode())
                : MoneyUtil.ZERO;
        BigDecimal totalCredit = MoneyUtil.round(netAmount.add(taxAmount), invoice.getCurrencyCode());

        if (!MoneyUtil.isPositive(totalCredit)) {
            throw LedgerException.badRequest("INVALID_CREDIT_AMOUNT", "Credit note total must be greater than zero");
        }
        if (totalCredit.compareTo(invoice.getOutstandingBalance()) > 0) {
            throw LedgerException.businessRule(
                    "CREDIT_EXCEEDS_OUTSTANDING",
                    "Credit note (" + MoneyUtil.toMoneyString(totalCredit)
                            + ") exceeds invoice outstanding balance (" + MoneyUtil.toMoneyString(invoice.getOutstandingBalance()) + ")"
            );
        }

        LocalDate creditDate = req.creditDate() != null ? req.creditDate() : LocalDate.now();
        String cnNumber = sequenceService.nextNumber(orgId, "CREDIT_NOTE", settings.getCreditNotePrefix(), creditDate.getYear());

        Account arAccount = accountService.requireBySystemCode(orgId, "AR");
        Account revAccount = invoice.getLines().isEmpty()
                ? accountService.requireBySystemCode(orgId, "SERVICE_REVENUE")
                : accountService.requireAccount(orgId, invoice.getLines().getFirst().getRevenueAccountId());
        Account taxPayableAccount = accountService.requireBySystemCode(orgId, "TAX_PAYABLE");

        BigDecimal baseTotal = MoneyUtil.convertToBase(totalCredit, invoice.getExchangeRate(), org.getBaseCurrency());
        BigDecimal baseTax = MoneyUtil.isPositive(taxAmount)
                ? MoneyUtil.convertToBase(taxAmount, invoice.getExchangeRate(), org.getBaseCurrency())
                : MoneyUtil.ZERO;
        BigDecimal baseNet = baseTotal.subtract(baseTax);

        List<JournalService.JournalLineInput> jLines = new ArrayList<>();
        if (MoneyUtil.isPositive(baseNet)) {
            jLines.add(JournalService.JournalLineInput.baseDebit(
                    revAccount.getId(), customer.getId(),
                    "Credit note " + cnNumber + " against " + invoice.getInvoiceNumber(),
                    netAmount, baseNet
            ));
        }
        if (MoneyUtil.isPositive(baseTax)) {
            jLines.add(JournalService.JournalLineInput.baseDebit(
                    taxPayableAccount.getId(), customer.getId(),
                    "Tax reversal for credit note " + cnNumber,
                    taxAmount, baseTax
            ));
        }
        jLines.add(JournalService.JournalLineInput.baseCredit(
                arAccount.getId(), customer.getId(),
                "AR reduction for credit note " + cnNumber,
                totalCredit, baseTotal
        ));

        UUID cnId = UUID.randomUUID();
        JournalEntry journal = journalService.postAutomatedJournal(
                orgId, principal.userId(), creditDate,
                "Credit Note " + cnNumber + " for invoice " + invoice.getInvoiceNumber() + ": " + req.reason().trim(),
                cnNumber, JournalSourceType.CREDIT_NOTE, cnId,
                "CREDIT_NOTE:" + cnId,
                invoice.getCurrencyCode(), invoice.getExchangeRate(), jLines
        );

        CreditNote cn = new CreditNote(
                orgId, cnNumber, invoice.getId(), customer.getId(), creditDate,
                req.reason().trim(), invoice.getCurrencyCode(), invoice.getExchangeRate(),
                netAmount, taxAmount, totalCredit, baseTotal, journal.getId(), principal.userId()
        );
        creditNoteRepository.save(cn);

        invoice.applyCredit(totalCredit);
        invoiceRepository.save(invoice);

        auditService.record(orgId, "CREDIT_NOTE_ISSUED", "CREDIT_NOTE", cn.getId().toString(),
                "Issued credit note " + cnNumber + " against " + invoice.getInvoiceNumber(),
                Map.of("amount", MoneyUtil.toMoneyString(totalCredit)));

        return CreditNoteDto.from(cn);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InvoiceDto voidInvoice(LedgerPrincipal principal, UUID invoiceId, String reason) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();

        Invoice invoice = invoiceRepository.findByIdAndOrganizationIdForUpdate(invoiceId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Invoice not found"));
        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw LedgerException.businessRule("ALREADY_VOID", "Invoice is already void");
        }
        if (MoneyUtil.isPositive(invoice.getAmountPaid()) || MoneyUtil.isPositive(invoice.getCreditApplied())) {
            throw LedgerException.businessRule(
                    "CANNOT_VOID_SETTLED_INVOICE",
                    "Cannot void an invoice with applied payments or credit notes. Reverse payments first."
            );
        }

        if (invoice.getJournalEntryId() != null) {
            journalService.reverseJournalInternal(
                    orgId, principal.userId(), invoice.getJournalEntryId(),
                    LocalDate.now(), "Void invoice " + invoice.getInvoiceNumber() + (reason != null ? ": " + reason : "")
            );
        }
        invoice.markVoid();
        invoiceRepository.save(invoice);

        auditService.record(orgId, "INVOICE_VOIDED", "INVOICE", invoice.getId().toString(),
                "Voided invoice " + invoice.getInvoiceNumber(), Map.of("reason", reason != null ? reason : ""));

        Contact customer = contactService.requireContact(orgId, invoice.getCustomerId());
        return toDto(invoice, customer.getName());
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto> listInvoices(UUID organizationId, InvoiceStatus status, UUID customerId) {
        List<Invoice> invoices = customerId != null
                ? invoiceRepository.findByOrganizationIdAndCustomerIdOrderByIssueDateDesc(organizationId, customerId)
                : invoiceRepository.findByOrganizationIdOrderByIssueDateDescCreatedAtDesc(organizationId);
        return invoices.stream()
                .filter(i -> status == null || i.getStatus() == status)
                .map(i -> {
                    Contact c = contactService.requireContact(organizationId, i.getCustomerId());
                    return toDto(i, c.getName());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceDto getInvoice(UUID organizationId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByIdAndOrganizationId(invoiceId, organizationId)
                .orElseThrow(() -> LedgerException.notFound("Invoice not found"));
        Contact c = contactService.requireContact(organizationId, invoice.getCustomerId());
        return toDto(invoice, c.getName());
    }

    @Transactional(readOnly = true)
    public Invoice requireInvoiceEntity(UUID organizationId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByIdAndOrganizationId(invoiceId, organizationId)
                .orElseThrow(() -> LedgerException.notFound("Invoice not found"));
        invoice.getLines().size();
        return invoice;
    }

    @Transactional(readOnly = true)
    public CreditNote requireCreditNoteEntity(UUID organizationId, UUID creditNoteId) {
        return creditNoteRepository.findByIdAndOrganizationId(creditNoteId, organizationId)
                .orElseThrow(() -> LedgerException.notFound("Credit note not found"));
    }

    private void computeAndAttachLines(Invoice invoice, Organization org, List<InvoiceLineRequest> lineReqs) {
        if (lineReqs == null || lineReqs.isEmpty()) {
            throw LedgerException.badRequest("LINES_REQUIRED", "Invoice must contain at least one line item");
        }
        String ccy = invoice.getCurrencyCode();
        BigDecimal subtotal = MoneyUtil.ZERO;
        BigDecimal discountTotal = MoneyUtil.ZERO;
        BigDecimal taxTotal = MoneyUtil.ZERO;
        BigDecimal grandTotal = MoneyUtil.ZERO;
        int lineNum = 1;

        for (InvoiceLineRequest lr : lineReqs) {
            if (lr.quantity() == null || !MoneyUtil.isPositive(lr.quantity())) {
                throw LedgerException.badRequest("INVALID_QUANTITY", "Line quantity must be positive");
            }
            if (lr.unitPrice() == null || MoneyUtil.isNegative(lr.unitPrice())) {
                throw LedgerException.badRequest("INVALID_UNIT_PRICE", "Line unit price cannot be negative");
            }
            Account revAcc = lr.revenueAccountId() != null
                    ? accountService.requirePostingAccount(org.getId(), lr.revenueAccountId())
                    : accountService.requireBySystemCode(org.getId(), "SERVICE_REVENUE");
            if (revAcc.getAccountType() != AccountType.REVENUE) {
                throw LedgerException.businessRule("INVALID_REVENUE_ACCOUNT", "Invoice line must post to a Revenue account");
            }

            BigDecimal grossBeforeDiscount = MoneyUtil.round(lr.quantity().multiply(lr.unitPrice()), ccy);
            BigDecimal discRate = lr.discountRate() != null ? lr.discountRate() : BigDecimal.ZERO;
            if (MoneyUtil.isNegative(discRate) || discRate.compareTo(MoneyUtil.ONE_HUNDRED) > 0) {
                throw LedgerException.badRequest("INVALID_DISCOUNT_RATE", "Discount rate must be between 0 and 100");
            }
            BigDecimal discountAmt = lr.discountAmount() != null && MoneyUtil.isPositive(lr.discountAmount())
                    ? MoneyUtil.round(lr.discountAmount(), ccy)
                    : MoneyUtil.percentageOf(grossBeforeDiscount, discRate, ccy);
            if (discountAmt.compareTo(grossBeforeDiscount) > 0) {
                throw LedgerException.badRequest("DISCOUNT_EXCEEDS_LINE", "Line discount cannot exceed line subtotal");
            }

            BigDecimal afterDiscount = MoneyUtil.round(grossBeforeDiscount.subtract(discountAmt), ccy);
            BigDecimal taxRate = lr.taxRate() != null ? lr.taxRate() : BigDecimal.ZERO;
            if (MoneyUtil.isNegative(taxRate)) {
                throw LedgerException.badRequest("INVALID_TAX_RATE", "Tax rate cannot be negative");
            }

            BigDecimal netAmount;
            BigDecimal taxAmount;
            BigDecimal lineTotal;
            if (invoice.isTaxInclusive()) {
                taxAmount = MoneyUtil.extractInclusiveTax(afterDiscount, taxRate, ccy);
                netAmount = MoneyUtil.round(afterDiscount.subtract(taxAmount), ccy);
                lineTotal = afterDiscount;
            } else {
                netAmount = afterDiscount;
                taxAmount = MoneyUtil.percentageOf(netAmount, taxRate, ccy);
                lineTotal = MoneyUtil.round(netAmount.add(taxAmount), ccy);
            }

            InvoiceLine line = new InvoiceLine(
                    org.getId(), lineNum++, lr.catalogItemId(), lr.description().trim(),
                    revAcc.getId(), lr.quantity().setScale(4, RoundingMode.HALF_UP),
                    MoneyUtil.round(lr.unitPrice(), ccy), discRate, discountAmt,
                    taxRate, taxAmount, netAmount, lineTotal
            );
            invoice.addLine(line);

            subtotal = subtotal.add(grossBeforeDiscount);
            discountTotal = discountTotal.add(discountAmt);
            taxTotal = taxTotal.add(taxAmount);
            grandTotal = grandTotal.add(lineTotal);
        }

        BigDecimal baseTotal = MoneyUtil.convertToBase(grandTotal, invoice.getExchangeRate(), org.getBaseCurrency());
        invoice.applyComputedTotals(subtotal, discountTotal, taxTotal, grandTotal, baseTotal);
    }

    private InvoiceDto toDto(Invoice inv, String customerName) {
        List<InvoiceLineDto> lines = inv.getLines().stream().map(l -> {
            Account acc = accountService.requireAccount(inv.getOrganizationId(), l.getRevenueAccountId());
            return new InvoiceLineDto(
                    l.getId(), l.getLineNumber(), l.getCatalogItemId(), l.getDescription(),
                    l.getRevenueAccountId(), acc.getCode(), acc.getName(),
                    l.getQuantity().stripTrailingZeros().toPlainString(),
                    MoneyUtil.toMoneyString(l.getUnitPrice()),
                    l.getDiscountRate().stripTrailingZeros().toPlainString(),
                    MoneyUtil.toMoneyString(l.getDiscountAmount()),
                    l.getTaxRate().stripTrailingZeros().toPlainString(),
                    MoneyUtil.toMoneyString(l.getTaxAmount()),
                    MoneyUtil.toMoneyString(l.getNetAmount()),
                    MoneyUtil.toMoneyString(l.getLineTotal())
            );
        }).toList();

        List<CreditNoteDto> credits = creditNoteRepository
                .findByOrganizationIdAndInvoiceIdOrderByCreditDateAsc(inv.getOrganizationId(), inv.getId())
                .stream()
                .map(CreditNoteDto::from)
                .toList();

        boolean overdue = inv.isOverdueOn(LocalDate.now());
        String displayStatus = overdue ? "OVERDUE" : inv.getStatus().name();

        return new InvoiceDto(
                inv.getId(), inv.getInvoiceNumber(), inv.getCustomerId(), customerName,
                inv.getStatus().name(), displayStatus, overdue,
                inv.getIssueDate(), inv.getDueDate(), inv.getPaymentTermsDays(),
                inv.getCurrencyCode(), inv.getExchangeRate().stripTrailingZeros().toPlainString(),
                inv.isTaxInclusive(),
                MoneyUtil.toMoneyString(inv.getSubtotal()),
                MoneyUtil.toMoneyString(inv.getDiscountTotal()),
                MoneyUtil.toMoneyString(inv.getTaxTotal()),
                MoneyUtil.toMoneyString(inv.getTotalAmount()),
                MoneyUtil.toMoneyString(inv.getBaseTotalAmount()),
                MoneyUtil.toMoneyString(inv.getAmountPaid()),
                MoneyUtil.toMoneyString(inv.getCreditApplied()),
                MoneyUtil.toMoneyString(inv.getOutstandingBalance()),
                inv.getNotes(), inv.getPaymentInstructions(), inv.getJournalEntryId(),
                inv.getIssuedAt(), inv.getCreatedAt(), lines, credits
        );
    }

    public record InvoiceLineRequest(
            UUID catalogItemId,
            @jakarta.validation.constraints.NotBlank String description,
            UUID revenueAccountId,
            @jakarta.validation.constraints.NotNull BigDecimal quantity,
            @jakarta.validation.constraints.NotNull BigDecimal unitPrice,
            BigDecimal discountRate,
            BigDecimal discountAmount,
            BigDecimal taxRate
    ) {}

    public record CreateInvoiceRequest(
            @jakarta.validation.constraints.NotNull UUID customerId,
            LocalDate issueDate,
            LocalDate dueDate,
            Integer paymentTermsDays,
            String currencyCode,
            BigDecimal exchangeRate,
            Boolean taxInclusive,
            String notes,
            String paymentInstructions,
            Boolean issueImmediately,
            @jakarta.validation.constraints.NotEmpty List<InvoiceLineRequest> lines
    ) {}

    public record UpdateInvoiceRequest(
            UUID customerId,
            LocalDate issueDate,
            LocalDate dueDate,
            Integer paymentTermsDays,
            String currencyCode,
            BigDecimal exchangeRate,
            Boolean taxInclusive,
            String notes,
            String paymentInstructions,
            List<InvoiceLineRequest> lines
    ) {}

    public record CreateCreditNoteRequest(
            LocalDate creditDate,
            @jakarta.validation.constraints.NotBlank String reason,
            @jakarta.validation.constraints.NotNull BigDecimal netAmount,
            BigDecimal taxAmount
    ) {}

    public record InvoiceLineDto(
            UUID id,
            int lineNumber,
            UUID catalogItemId,
            String description,
            UUID revenueAccountId,
            String revenueAccountCode,
            String revenueAccountName,
            String quantity,
            String unitPrice,
            String discountRate,
            String discountAmount,
            String taxRate,
            String taxAmount,
            String netAmount,
            String lineTotal
    ) {}

    public record CreditNoteDto(
            UUID id,
            String creditNoteNumber,
            UUID invoiceId,
            LocalDate creditDate,
            String reason,
            String currencyCode,
            String netAmount,
            String taxAmount,
            String totalAmount,
            UUID journalEntryId
    ) {
        static CreditNoteDto from(CreditNote cn) {
            return new CreditNoteDto(
                    cn.getId(), cn.getCreditNoteNumber(), cn.getInvoiceId(), cn.getCreditDate(),
                    cn.getReason(), cn.getCurrencyCode(),
                    MoneyUtil.toMoneyString(cn.getNetAmount()),
                    MoneyUtil.toMoneyString(cn.getTaxAmount()),
                    MoneyUtil.toMoneyString(cn.getTotalAmount()),
                    cn.getJournalEntryId()
            );
        }
    }

    public record InvoiceDto(
            UUID id,
            String invoiceNumber,
            UUID customerId,
            String customerName,
            String status,
            String displayStatus,
            boolean overdue,
            LocalDate issueDate,
            LocalDate dueDate,
            int paymentTermsDays,
            String currencyCode,
            String exchangeRate,
            boolean taxInclusive,
            String subtotal,
            String discountTotal,
            String taxTotal,
            String totalAmount,
            String baseTotalAmount,
            String amountPaid,
            String creditApplied,
            String outstandingBalance,
            String notes,
            String paymentInstructions,
            UUID journalEntryId,
            Instant issuedAt,
            Instant createdAt,
            List<InvoiceLineDto> lines,
            List<CreditNoteDto> creditNotes
    ) {}
}
