package com.ledger.contact;

import com.ledger.accounting.Account;
import com.ledger.accounting.AccountService;
import com.ledger.accounting.AccountType;
import com.ledger.audit.AuditService;
import com.ledger.expense.Expense;
import com.ledger.expense.ExpenseRepository;
import com.ledger.expense.ExpenseStatus;
import com.ledger.expense.ExpenseType;
import com.ledger.invoice.Invoice;
import com.ledger.invoice.InvoiceRepository;
import com.ledger.invoice.InvoiceStatus;
import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationService;
import com.ledger.payment.Payment;
import com.ledger.payment.PaymentRepository;
import com.ledger.payment.PaymentStatus;
import com.ledger.shared.LedgerException;
import com.ledger.shared.LedgerPrincipal;
import com.ledger.shared.MoneyUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ContactService {

    private final ContactRepository contactRepository;
    private final CatalogItemRepository catalogRepository;
    private final InvoiceRepository invoiceRepository;
    private final ExpenseRepository expenseRepository;
    private final PaymentRepository paymentRepository;
    private final AccountService accountService;
    private final OrganizationService organizationService;
    private final AuditService auditService;

    public ContactService(
            ContactRepository contactRepository,
            CatalogItemRepository catalogRepository,
            InvoiceRepository invoiceRepository,
            ExpenseRepository expenseRepository,
            PaymentRepository paymentRepository,
            AccountService accountService,
            OrganizationService organizationService,
            AuditService auditService) {
        this.contactRepository = contactRepository;
        this.catalogRepository = catalogRepository;
        this.invoiceRepository = invoiceRepository;
        this.expenseRepository = expenseRepository;
        this.paymentRepository = paymentRepository;
        this.accountService = accountService;
        this.organizationService = organizationService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ContactSummaryDto> listContacts(UUID organizationId, ContactType type) {
        List<Contact> contacts = type == null
                ? contactRepository.findByOrganizationIdOrderByNameAsc(organizationId)
                : contactRepository.findByOrganizationIdAndContactTypeInOrderByNameAsc(
                        organizationId,
                        type == ContactType.BOTH ? List.of(ContactType.BOTH) : List.of(type, ContactType.BOTH)
                );
        return contacts.stream().map(c -> summarizeContact(organizationId, c)).toList();
    }

    @Transactional(readOnly = true)
    public ContactDetailDto getContactDetail(UUID organizationId, UUID contactId) {
        Contact contact = requireContact(organizationId, contactId);
        ContactSummaryDto summary = summarizeContact(organizationId, contact);

        List<ContactTransactionRow> invoices = invoiceRepository
                .findByOrganizationIdAndCustomerIdOrderByIssueDateDesc(organizationId, contactId)
                .stream()
                .map(i -> new ContactTransactionRow(
                        i.getId(), "INVOICE", i.getInvoiceNumber(), i.getIssueDate(), i.getDueDate(),
                        i.getStatus().name(), i.getCurrencyCode(),
                        MoneyUtil.toMoneyString(i.getTotalAmount()),
                        MoneyUtil.toMoneyString(i.getOutstandingBalance())
                ))
                .toList();

        List<ContactTransactionRow> expenses = expenseRepository
                .findByOrganizationIdAndSupplierIdOrderByExpenseDateDesc(organizationId, contactId)
                .stream()
                .map(e -> new ContactTransactionRow(
                        e.getId(), e.getExpenseType().name(), e.getExpenseNumber(), e.getExpenseDate(), e.getDueDate(),
                        e.getStatus().name(), e.getCurrencyCode(),
                        MoneyUtil.toMoneyString(e.getTotalAmount()),
                        MoneyUtil.toMoneyString(e.getOutstandingBalance())
                ))
                .toList();

        List<ContactTransactionRow> payments = paymentRepository
                .findByOrganizationIdAndContactIdOrderByPaymentDateDesc(organizationId, contactId)
                .stream()
                .map(p -> new ContactTransactionRow(
                        p.getId(), p.getPaymentType().name(), p.getPaymentNumber(), p.getPaymentDate(), null,
                        p.getStatus().name(), p.getCurrencyCode(),
                        MoneyUtil.toMoneyString(p.getAmount()),
                        MoneyUtil.toMoneyString(p.getUnallocatedAmount())
                ))
                .toList();

        return new ContactDetailDto(summary, invoices, expenses, payments);
    }

    @Transactional
    public ContactSummaryDto createContact(LedgerPrincipal principal, CreateContactRequest req) {
        principal.requireFinanceOperations();
        UUID orgId = principal.requireOrganizationId();
        Organization org = organizationService.getOrganizationEntity(orgId);
        String ccy = (req.currencyCode() == null || req.currencyCode().isBlank())
                ? org.getBaseCurrency() : req.currencyCode().toUpperCase();
        int terms = req.paymentTermsDays() != null ? req.paymentTermsDays() : 30;

        Contact contact = new Contact(
                orgId, req.contactType(), req.name(), req.contactPerson(), req.email(),
                req.phone(), req.billingAddress(), req.taxId(), ccy, terms, req.notes()
        );
        contactRepository.save(contact);

        auditService.record(orgId, "CONTACT_CREATED", "CONTACT", contact.getId().toString(),
                "Created contact " + contact.getName() + " (" + contact.getContactType() + ")", Map.of());
        return summarizeContact(orgId, contact);
    }

    @Transactional
    public ContactSummaryDto updateContact(LedgerPrincipal principal, UUID contactId, UpdateContactRequest req) {
        principal.requireFinanceOperations();
        UUID orgId = principal.requireOrganizationId();
        Contact contact = requireContact(orgId, contactId);

        if (req.contactType() != null) contact.setContactType(req.contactType());
        if (req.name() != null && !req.name().isBlank()) contact.setName(req.name());
        if (req.contactPerson() != null) contact.setContactPerson(req.contactPerson());
        if (req.email() != null) contact.setEmail(req.email());
        if (req.phone() != null) contact.setPhone(req.phone());
        if (req.billingAddress() != null) contact.setBillingAddress(req.billingAddress());
        if (req.taxId() != null) contact.setTaxId(req.taxId());
        if (req.currencyCode() != null && !req.currencyCode().isBlank()) contact.setCurrencyCode(req.currencyCode().toUpperCase());
        if (req.paymentTermsDays() != null && req.paymentTermsDays() >= 0) contact.setPaymentTermsDays(req.paymentTermsDays());
        if (req.notes() != null) contact.setNotes(req.notes());
        if (req.active() != null) contact.setActive(req.active());
        contactRepository.save(contact);

        return summarizeContact(orgId, contact);
    }

    @Transactional(readOnly = true)
    public Contact requireContact(UUID organizationId, UUID contactId) {
        return contactRepository.findByIdAndOrganizationId(contactId, organizationId)
                .orElseThrow(() -> LedgerException.notFound("Contact not found"));
    }

    @Transactional(readOnly = true)
    public List<CatalogItemDto> listCatalog(UUID organizationId) {
        return catalogRepository.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .map(this::toCatalogDto)
                .toList();
    }

    @Transactional
    public CatalogItemDto createCatalogItem(LedgerPrincipal principal, CreateCatalogItemRequest req) {
        principal.requireFinanceOperations();
        UUID orgId = principal.requireOrganizationId();
        Organization org = organizationService.getOrganizationEntity(orgId);

        Account revAccount = req.revenueAccountId() != null
                ? accountService.requirePostingAccount(orgId, req.revenueAccountId())
                : accountService.requireBySystemCode(orgId, "SERVICE_REVENUE");
        if (revAccount.getAccountType() != AccountType.REVENUE) {
            throw LedgerException.businessRule("INVALID_REVENUE_ACCOUNT", "Catalog item must reference a Revenue account");
        }

        String ccy = (req.currencyCode() == null || req.currencyCode().isBlank())
                ? org.getBaseCurrency() : req.currencyCode().toUpperCase();
        String sku = (req.sku() == null || req.sku().isBlank()) ? null : req.sku().trim().toUpperCase();

        CatalogItem item = new CatalogItem(
                orgId, sku, req.name(), req.description(),
                MoneyUtil.round(req.unitPrice(), ccy), ccy,
                revAccount.getId(), req.defaultTaxRate() != null ? req.defaultTaxRate() : BigDecimal.ZERO
        );
        catalogRepository.save(item);
        return toCatalogDto(item);
    }

    @Transactional
    public CatalogItemDto updateCatalogItem(LedgerPrincipal principal, UUID itemId, UpdateCatalogItemRequest req) {
        principal.requireFinanceOperations();
        UUID orgId = principal.requireOrganizationId();
        CatalogItem item = catalogRepository.findByIdAndOrganizationId(itemId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Catalog item not found"));

        if (req.sku() != null) item.setSku(req.sku().isBlank() ? null : req.sku().trim().toUpperCase());
        if (req.name() != null && !req.name().isBlank()) item.setName(req.name());
        if (req.description() != null) item.setDescription(req.description());
        if (req.unitPrice() != null) item.setUnitPrice(MoneyUtil.round(req.unitPrice(), item.getCurrencyCode()));
        if (req.revenueAccountId() != null) {
            Account acc = accountService.requirePostingAccount(orgId, req.revenueAccountId());
            if (acc.getAccountType() != AccountType.REVENUE) {
                throw LedgerException.businessRule("INVALID_REVENUE_ACCOUNT", "Catalog item must reference a Revenue account");
            }
            item.setRevenueAccountId(acc.getId());
        }
        if (req.defaultTaxRate() != null) item.setDefaultTaxRate(req.defaultTaxRate());
        if (req.active() != null) item.setActive(req.active());
        catalogRepository.save(item);
        return toCatalogDto(item);
    }

    private ContactSummaryDto summarizeContact(UUID orgId, Contact c) {
        LocalDate today = LocalDate.now();
        BigDecimal totalInvoiced = MoneyUtil.ZERO;
        BigDecimal totalReceived = MoneyUtil.ZERO;
        BigDecimal arOutstanding = MoneyUtil.ZERO;
        int overdueInvoices = 0;

        for (Invoice inv : invoiceRepository.findByOrganizationIdAndCustomerIdOrderByIssueDateDesc(orgId, c.getId())) {
            if (inv.getStatus() == InvoiceStatus.ISSUED || inv.getStatus() == InvoiceStatus.PARTIALLY_PAID || inv.getStatus() == InvoiceStatus.PAID) {
                totalInvoiced = totalInvoiced.add(inv.getBaseTotalAmount());
                BigDecimal invOutstandingBase = MoneyUtil.convertToBase(inv.getOutstandingBalance(), inv.getExchangeRate(), organizationService.getOrganizationEntity(orgId).getBaseCurrency());
                arOutstanding = arOutstanding.add(invOutstandingBase);
                if (inv.isOverdueOn(today)) {
                    overdueInvoices++;
                }
            }
        }

        BigDecimal totalBills = MoneyUtil.ZERO;
        BigDecimal apOutstanding = MoneyUtil.ZERO;
        for (Expense exp : expenseRepository.findByOrganizationIdAndSupplierIdOrderByExpenseDateDesc(orgId, c.getId())) {
            if (exp.getStatus() == ExpenseStatus.POSTED) {
                totalBills = totalBills.add(exp.getBaseTotalAmount());
                if (exp.getExpenseType() == ExpenseType.SUPPLIER_BILL) {
                    apOutstanding = apOutstanding.add(MoneyUtil.convertToBase(exp.getOutstandingBalance(), exp.getExchangeRate(), organizationService.getOrganizationEntity(orgId).getBaseCurrency()));
                }
            }
        }

        BigDecimal totalPaidToSupplier = MoneyUtil.ZERO;
        for (Payment p : paymentRepository.findByOrganizationIdAndContactIdOrderByPaymentDateDesc(orgId, c.getId())) {
            if (p.getStatus() == PaymentStatus.POSTED) {
                if (p.getPaymentType() == com.ledger.payment.PaymentType.CUSTOMER_RECEIPT) {
                    totalReceived = totalReceived.add(p.getBaseAmount());
                } else {
                    totalPaidToSupplier = totalPaidToSupplier.add(p.getBaseAmount());
                }
            }
        }

        return new ContactSummaryDto(
                c.getId(), c.getContactType(), c.getName(), c.getContactPerson(), c.getEmail(),
                c.getPhone(), c.getBillingAddress(), c.getTaxId(), c.getCurrencyCode(),
                c.getPaymentTermsDays(), c.getNotes(), c.isActive(), c.getCreatedAt(),
                MoneyUtil.toMoneyString(totalInvoiced),
                MoneyUtil.toMoneyString(totalReceived),
                MoneyUtil.toMoneyString(arOutstanding),
                overdueInvoices,
                MoneyUtil.toMoneyString(totalBills),
                MoneyUtil.toMoneyString(totalPaidToSupplier),
                MoneyUtil.toMoneyString(apOutstanding)
        );
    }

    private CatalogItemDto toCatalogDto(CatalogItem item) {
        Account acc = accountService.requireAccount(item.getOrganizationId(), item.getRevenueAccountId());
        return new CatalogItemDto(
                item.getId(), item.getSku(), item.getName(), item.getDescription(),
                MoneyUtil.toMoneyString(item.getUnitPrice()), item.getCurrencyCode(),
                item.getRevenueAccountId(), acc.getCode(), acc.getName(),
                item.getDefaultTaxRate().stripTrailingZeros().toPlainString(), item.isActive()
        );
    }

    public record CreateContactRequest(
            @jakarta.validation.constraints.NotNull ContactType contactType,
            @jakarta.validation.constraints.NotBlank String name,
            String contactPerson,
            String email,
            String phone,
            String billingAddress,
            String taxId,
            String currencyCode,
            Integer paymentTermsDays,
            String notes
    ) {}

    public record UpdateContactRequest(
            ContactType contactType,
            String name,
            String contactPerson,
            String email,
            String phone,
            String billingAddress,
            String taxId,
            String currencyCode,
            Integer paymentTermsDays,
            String notes,
            Boolean active
    ) {}

    public record CreateCatalogItemRequest(
            String sku,
            @jakarta.validation.constraints.NotBlank String name,
            String description,
            @jakarta.validation.constraints.NotNull BigDecimal unitPrice,
            String currencyCode,
            UUID revenueAccountId,
            BigDecimal defaultTaxRate
    ) {}

    public record UpdateCatalogItemRequest(
            String sku,
            String name,
            String description,
            BigDecimal unitPrice,
            UUID revenueAccountId,
            BigDecimal defaultTaxRate,
            Boolean active
    ) {}

    public record ContactSummaryDto(
            UUID id,
            ContactType contactType,
            String name,
            String contactPerson,
            String email,
            String phone,
            String billingAddress,
            String taxId,
            String currencyCode,
            int paymentTermsDays,
            String notes,
            boolean active,
            Instant createdAt,
            String totalInvoiced,
            String totalReceived,
            String receivableOutstanding,
            int overdueInvoiceCount,
            String totalBilled,
            String totalPaid,
            String payableOutstanding
    ) {}

    public record ContactTransactionRow(
            UUID id,
            String type,
            String reference,
            LocalDate date,
            LocalDate dueDate,
            String status,
            String currencyCode,
            String totalAmount,
            String balanceAmount
    ) {}

    public record ContactDetailDto(
            ContactSummaryDto contact,
            List<ContactTransactionRow> invoices,
            List<ContactTransactionRow> expenses,
            List<ContactTransactionRow> payments
    ) {}

    public record CatalogItemDto(
            UUID id,
            String sku,
            String name,
            String description,
            String unitPrice,
            String currencyCode,
            UUID revenueAccountId,
            String revenueAccountCode,
            String revenueAccountName,
            String defaultTaxRate,
            boolean active
    ) {}
}
