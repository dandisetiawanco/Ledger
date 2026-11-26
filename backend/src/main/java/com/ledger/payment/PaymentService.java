package com.ledger.payment;

import com.ledger.accounting.Account;
import com.ledger.accounting.AccountService;
import com.ledger.accounting.AccountType;
import com.ledger.audit.AuditService;
import com.ledger.contact.Contact;
import com.ledger.contact.ContactService;
import com.ledger.expense.Expense;
import com.ledger.expense.ExpenseRepository;
import com.ledger.expense.ExpenseStatus;
import com.ledger.expense.ExpenseType;
import com.ledger.invoice.Invoice;
import com.ledger.invoice.InvoiceRepository;
import com.ledger.invoice.InvoiceStatus;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final ExpenseRepository expenseRepository;
    private final ContactService contactService;
    private final AccountService accountService;
    private final OrganizationService organizationService;
    private final JournalService journalService;
    private final DocumentSequenceService sequenceService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(
            PaymentRepository paymentRepository,
            InvoiceRepository invoiceRepository,
            ExpenseRepository expenseRepository,
            ContactService contactService,
            AccountService accountService,
            OrganizationService organizationService,
            JournalService journalService,
            DocumentSequenceService sequenceService,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.expenseRepository = expenseRepository;
        this.contactService = contactService;
        this.accountService = accountService;
        this.organizationService = organizationService;
        this.journalService = journalService;
        this.sequenceService = sequenceService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentDto recordPayment(LedgerPrincipal principal, RecordPaymentRequest req, String headerIdempotencyKey) {
        principal.requireFinanceOperations();
        UUID orgId = principal.requireOrganizationId();

        String idemKey = (headerIdempotencyKey != null && !headerIdempotencyKey.isBlank())
                ? headerIdempotencyKey.trim()
                : (req.idempotencyKey() != null && !req.idempotencyKey().isBlank() ? req.idempotencyKey().trim() : null);

        if (idemKey != null) {
            Optional<Payment> existing = paymentRepository.findByOrganizationIdAndIdempotencyKey(orgId, idemKey);
            if (existing.isPresent()) {
                return toDto(existing.get());
            }
        }

        Organization org = organizationService.getOrganizationEntity(orgId);
        FinancialSettings settings = organizationService.getSettingsEntity(orgId);
        Contact contact = contactService.requireContact(orgId, req.contactId());

        if (req.paymentType() == PaymentType.CUSTOMER_RECEIPT && !contact.isCustomer()) {
            throw LedgerException.businessRule("CONTACT_NOT_CUSTOMER", "Contact must be a customer for a customer receipt");
        }
        if (req.paymentType() == PaymentType.SUPPLIER_PAYMENT && !contact.isSupplier()) {
            throw LedgerException.businessRule("CONTACT_NOT_SUPPLIER", "Contact must be a supplier for a supplier payment");
        }

        Account bankAcc = req.bankAccountId() != null
                ? accountService.requirePostingAccount(orgId, req.bankAccountId())
                : accountService.requireBySystemCode(orgId, "BANK");
        if (bankAcc.getAccountType() != AccountType.ASSET) {
            throw LedgerException.businessRule("INVALID_BANK_ACCOUNT", "Payment requires a Cash or Bank asset account");
        }

        LocalDate payDate = req.paymentDate() != null ? req.paymentDate() : LocalDate.now();
        String ccy = (req.currencyCode() == null || req.currencyCode().isBlank())
                ? contact.getCurrencyCode() : req.currencyCode().toUpperCase(Locale.ROOT);
        BigDecimal fxRate = organizationService.resolveExchangeRate(orgId, ccy, payDate, req.exchangeRate());

        if (req.amount() == null || !MoneyUtil.isPositive(req.amount())) {
            throw LedgerException.badRequest("INVALID_AMOUNT", "Payment amount must be greater than zero");
        }
        BigDecimal totalAmount = MoneyUtil.round(req.amount(), ccy);
        BigDecimal baseTotalAmount = MoneyUtil.convertToBase(totalAmount, fxRate, org.getBaseCurrency());

        List<AllocationInput> allocInputs = req.allocations() != null ? req.allocations() : List.of();
        BigDecimal allocatedSum = MoneyUtil.ZERO;
        for (AllocationInput ai : allocInputs) {
            if (ai.amount() == null || !MoneyUtil.isPositive(ai.amount())) {
                throw LedgerException.badRequest("INVALID_ALLOCATION", "Allocation amount must be positive");
            }
            allocatedSum = allocatedSum.add(MoneyUtil.round(ai.amount(), ccy));
        }

        if (allocatedSum.compareTo(totalAmount) > 0) {
            throw LedgerException.businessRule(
                    "ALLOCATION_EXCEEDS_PAYMENT",
                    "Total allocated amount (" + MoneyUtil.toMoneyString(allocatedSum)
                            + ") exceeds payment amount (" + MoneyUtil.toMoneyString(totalAmount) + ")"
            );
        }

        BigDecimal unallocated = MoneyUtil.round(totalAmount.subtract(allocatedSum), ccy);
        if (req.paymentType() == PaymentType.SUPPLIER_PAYMENT && MoneyUtil.isPositive(unallocated) && allocInputs.isEmpty()) {
            throw LedgerException.businessRule("BILL_ALLOCATION_REQUIRED", "Supplier payments must allocate against one or more posted supplier bills");
        }

        String payNumber = sequenceService.nextNumber(orgId, "PAYMENT", settings.getPaymentPrefix(), payDate.getYear());
        PaymentMethod method = req.paymentMethod() != null ? req.paymentMethod() : PaymentMethod.BANK_TRANSFER;

        Payment payment = new Payment(
                orgId, payNumber, req.paymentType(), contact.getId(), payDate, method,
                bankAcc.getId(), ccy, fxRate, totalAmount, baseTotalAmount,
                allocatedSum, unallocated, req.referenceNumber(), req.notes(), idemKey, principal.userId()
        );

        List<JournalService.JournalLineInput> jLines = new ArrayList<>();
        if (req.paymentType() == PaymentType.CUSTOMER_RECEIPT) {
            buildCustomerReceiptJournal(org, contact, payment, bankAcc, allocInputs, jLines);
        } else {
            buildSupplierPaymentJournal(org, contact, payment, bankAcc, allocInputs, jLines);
        }

        JournalEntry journal = journalService.postAutomatedJournal(
                orgId, principal.userId(), payDate,
                (req.paymentType() == PaymentType.CUSTOMER_RECEIPT ? "Customer receipt " : "Supplier payment ")
                        + payNumber + " - " + contact.getName(),
                req.referenceNumber() != null && !req.referenceNumber().isBlank() ? req.referenceNumber() : payNumber,
                JournalSourceType.PAYMENT, payment.getId(),
                "PAYMENT_POST:" + payment.getId(),
                ccy, fxRate, jLines
        );

        payment.attachJournal(journal.getId());
        paymentRepository.save(payment);

        auditService.record(orgId, "PAYMENT_RECORDED", "PAYMENT", payment.getId().toString(),
                "Recorded " + req.paymentType() + " " + payNumber + " (" + ccy + " " + MoneyUtil.toMoneyString(totalAmount) + ")",
                Map.of("journalEntryId", journal.getId().toString(), "unallocated", MoneyUtil.toMoneyString(unallocated)));
        eventPublisher.publishEvent(new DomainEvents.PaymentRecordedEvent(
                orgId, payment.getId(), payNumber, req.paymentType().name(), totalAmount, ccy));

        return toDto(payment);
    }

    private void buildCustomerReceiptJournal(
            Organization org, Contact customer, Payment payment, Account bankAcc,
            List<AllocationInput> allocInputs, List<JournalService.JournalLineInput> jLines
    ) {
        UUID orgId = org.getId();
        Account arAccount = accountService.requireBySystemCode(orgId, "AR");
        Account fxGainAccount = accountService.requireBySystemCode(orgId, "FX_GAIN");
        Account fxLossAccount = accountService.requireBySystemCode(orgId, "FX_LOSS");
        Account customerCreditAccount = accountService.requireBySystemCode(orgId, "CUSTOMER_CREDIT");

        // Debit Bank for full base cash received
        jLines.add(JournalService.JournalLineInput.baseDebit(
                bankAcc.getId(), customer.getId(),
                "Receipt " + payment.getPaymentNumber() + " from " + customer.getName(),
                payment.getAmount(), payment.getBaseAmount()
        ));

        BigDecimal totalArBaseRelieved = MoneyUtil.ZERO;
        BigDecimal totalFxDiff = MoneyUtil.ZERO;

        for (AllocationInput ai : allocInputs) {
            if (ai.invoiceId() == null) {
                throw LedgerException.badRequest("INVOICE_ID_REQUIRED", "Customer receipt allocation requires invoiceId");
            }
            Invoice inv = invoiceRepository.findByIdAndOrganizationIdForUpdate(ai.invoiceId(), orgId)
                    .orElseThrow(() -> LedgerException.notFound("Invoice not found"));
            if (!inv.getCustomerId().equals(customer.getId())) {
                throw LedgerException.businessRule("CONTACT_MISMATCH", "Invoice belongs to a different customer");
            }
            if (inv.getStatus() != InvoiceStatus.ISSUED && inv.getStatus() != InvoiceStatus.PARTIALLY_PAID) {
                throw LedgerException.businessRule("INVOICE_NOT_OPEN", "Invoice " + inv.getInvoiceNumber() + " is not open for payment");
            }
            if (!inv.getCurrencyCode().equals(payment.getCurrencyCode())) {
                throw LedgerException.businessRule("CURRENCY_MISMATCH", "Payment currency must match invoice currency (" + inv.getCurrencyCode() + ")");
            }

            BigDecimal allocAmt = MoneyUtil.round(ai.amount(), payment.getCurrencyCode());
            if (allocAmt.compareTo(inv.getOutstandingBalance()) > 0) {
                throw LedgerException.businessRule(
                        "OVER_ALLOCATION",
                        "Allocation (" + MoneyUtil.toMoneyString(allocAmt) + ") exceeds invoice "
                                + inv.getInvoiceNumber() + " outstanding balance (" + MoneyUtil.toMoneyString(inv.getOutstandingBalance()) + ")"
                );
            }

            BigDecimal paidBase = MoneyUtil.convertToBase(allocAmt, payment.getExchangeRate(), org.getBaseCurrency());
            BigDecimal arHistoricalBase = MoneyUtil.convertToBase(allocAmt, inv.getExchangeRate(), org.getBaseCurrency());
            BigDecimal fxGainLoss = paidBase.subtract(arHistoricalBase);

            inv.applyPayment(allocAmt);
            invoiceRepository.save(inv);

            PaymentAllocation allocation = new PaymentAllocation(
                    orgId, inv.getId(), null, payment.getPaymentDate(),
                    allocAmt, paidBase, fxGainLoss
            );
            payment.addAllocation(allocation);

            jLines.add(JournalService.JournalLineInput.baseCredit(
                    arAccount.getId(), customer.getId(),
                    "Payment allocated to invoice " + inv.getInvoiceNumber(),
                    allocAmt, arHistoricalBase
            ));
            totalArBaseRelieved = totalArBaseRelieved.add(arHistoricalBase);
            totalFxDiff = totalFxDiff.add(fxGainLoss);
        }

        if (MoneyUtil.isPositive(payment.getUnallocatedAmount())) {
            BigDecimal unallocBase = payment.getBaseAmount().subtract(totalArBaseRelieved).subtract(totalFxDiff);
            if (MoneyUtil.isPositive(unallocBase)) {
                jLines.add(JournalService.JournalLineInput.baseCredit(
                        customerCreditAccount.getId(), customer.getId(),
                        "Unallocated customer credit from " + payment.getPaymentNumber(),
                        payment.getUnallocatedAmount(), unallocBase
                ));
            }
        }

        // Balance realized FX gain (credit) or loss (debit)
        if (MoneyUtil.isPositive(totalFxDiff)) {
            jLines.add(JournalService.JournalLineInput.baseCredit(
                    fxGainAccount.getId(), customer.getId(),
                    "Realized FX gain on " + payment.getPaymentNumber(),
                    BigDecimal.ZERO, totalFxDiff
            ));
        } else if (MoneyUtil.isNegative(totalFxDiff)) {
            jLines.add(JournalService.JournalLineInput.baseDebit(
                    fxLossAccount.getId(), customer.getId(),
                    "Realized FX loss on " + payment.getPaymentNumber(),
                    BigDecimal.ZERO, totalFxDiff.abs()
            ));
        }
    }

    private void buildSupplierPaymentJournal(
            Organization org, Contact supplier, Payment payment, Account bankAcc,
            List<AllocationInput> allocInputs, List<JournalService.JournalLineInput> jLines
    ) {
        UUID orgId = org.getId();
        Account apAccount = accountService.requireBySystemCode(orgId, "AP");
        Account fxGainAccount = accountService.requireBySystemCode(orgId, "FX_GAIN");
        Account fxLossAccount = accountService.requireBySystemCode(orgId, "FX_LOSS");

        BigDecimal totalFxDiff = MoneyUtil.ZERO; // positive = gain (paid less base than AP booked)

        for (AllocationInput ai : allocInputs) {
            if (ai.expenseId() == null) {
                throw LedgerException.badRequest("EXPENSE_ID_REQUIRED", "Supplier payment allocation requires expenseId (bill ID)");
            }
            Expense bill = expenseRepository.findByIdAndOrganizationIdForUpdate(ai.expenseId(), orgId)
                    .orElseThrow(() -> LedgerException.notFound("Supplier bill not found"));
            if (bill.getExpenseType() != ExpenseType.SUPPLIER_BILL || bill.getStatus() != ExpenseStatus.POSTED) {
                throw LedgerException.businessRule("BILL_NOT_PAYABLE", "Only posted supplier bills can be settled");
            }
            if (!supplier.getId().equals(bill.getSupplierId())) {
                throw LedgerException.businessRule("CONTACT_MISMATCH", "Supplier bill belongs to a different supplier");
            }
            if (!bill.getCurrencyCode().equals(payment.getCurrencyCode())) {
                throw LedgerException.businessRule("CURRENCY_MISMATCH", "Payment currency must match bill currency");
            }

            BigDecimal allocAmt = MoneyUtil.round(ai.amount(), payment.getCurrencyCode());
            if (allocAmt.compareTo(bill.getOutstandingBalance()) > 0) {
                throw LedgerException.businessRule(
                        "OVER_ALLOCATION",
                        "Allocation exceeds bill " + bill.getExpenseNumber() + " outstanding balance"
                );
            }

            BigDecimal paidBase = MoneyUtil.convertToBase(allocAmt, payment.getExchangeRate(), org.getBaseCurrency());
            BigDecimal apHistoricalBase = MoneyUtil.convertToBase(allocAmt, bill.getExchangeRate(), org.getBaseCurrency());
            BigDecimal fxGainLoss = apHistoricalBase.subtract(paidBase);

            bill.applyBillPayment(allocAmt);
            expenseRepository.save(bill);

            PaymentAllocation allocation = new PaymentAllocation(
                    orgId, null, bill.getId(), payment.getPaymentDate(),
                    allocAmt, paidBase, fxGainLoss
            );
            payment.addAllocation(allocation);

            jLines.add(JournalService.JournalLineInput.baseDebit(
                    apAccount.getId(), supplier.getId(),
                    "Settlement of supplier bill " + bill.getExpenseNumber(),
                    allocAmt, apHistoricalBase
            ));
            totalFxDiff = totalFxDiff.add(fxGainLoss);
        }

        // Credit Bank for base cash disbursed
        jLines.add(JournalService.JournalLineInput.baseCredit(
                bankAcc.getId(), supplier.getId(),
                "Payment " + payment.getPaymentNumber() + " to " + supplier.getName(),
                payment.getAmount(), payment.getBaseAmount()
        ));

        if (MoneyUtil.isPositive(totalFxDiff)) {
            jLines.add(JournalService.JournalLineInput.baseCredit(
                    fxGainAccount.getId(), supplier.getId(),
                    "Realized FX gain on supplier payment " + payment.getPaymentNumber(),
                    BigDecimal.ZERO, totalFxDiff
            ));
        } else if (MoneyUtil.isNegative(totalFxDiff)) {
            jLines.add(JournalService.JournalLineInput.baseDebit(
                    fxLossAccount.getId(), supplier.getId(),
                    "Realized FX loss on supplier payment " + payment.getPaymentNumber(),
                    BigDecimal.ZERO, totalFxDiff.abs()
            ));
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentDto reversePayment(LedgerPrincipal principal, UUID paymentId, String reason) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();

        Payment payment = paymentRepository.findByIdAndOrganizationId(paymentId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Payment not found"));
        if (payment.getStatus() == PaymentStatus.REVERSED) {
            throw LedgerException.businessRule("ALREADY_REVERSED", "Payment has already been reversed");
        }

        JournalEntry reversal = journalService.reverseJournalInternal(
                orgId, principal.userId(), payment.getJournalEntryId(),
                LocalDate.now(), "Reversal of payment " + payment.getPaymentNumber() + (reason != null ? ": " + reason : "")
        );

        for (PaymentAllocation alloc : payment.getAllocations()) {
            if (alloc.getInvoiceId() != null) {
                Invoice inv = invoiceRepository.findByIdAndOrganizationIdForUpdate(alloc.getInvoiceId(), orgId).orElseThrow();
                inv.reversePayment(alloc.getAllocatedAmount());
                invoiceRepository.save(inv);
            }
            if (alloc.getExpenseId() != null) {
                Expense bill = expenseRepository.findByIdAndOrganizationIdForUpdate(alloc.getExpenseId(), orgId).orElseThrow();
                bill.reverseBillPayment(alloc.getAllocatedAmount());
                expenseRepository.save(bill);
            }
        }

        payment.markReversed(reversal.getId());
        paymentRepository.save(payment);

        auditService.record(orgId, "PAYMENT_REVERSED", "PAYMENT", payment.getId().toString(),
                "Reversed payment " + payment.getPaymentNumber(), Map.of("reversalJournalId", reversal.getId().toString()));
        return toDto(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> listPayments(UUID organizationId, PaymentType type) {
        return paymentRepository.findByOrganizationIdOrderByPaymentDateDescCreatedAtDesc(organizationId).stream()
                .filter(p -> type == null || p.getPaymentType() == type)
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Payment requirePaymentEntity(UUID organizationId, UUID paymentId) {
        return paymentRepository.findByIdAndOrganizationId(paymentId, organizationId)
                .orElseThrow(() -> LedgerException.notFound("Payment not found"));
    }

    private PaymentDto toDto(Payment p) {
        Contact contact = contactService.requireContact(p.getOrganizationId(), p.getContactId());
        Account bank = accountService.requireAccount(p.getOrganizationId(), p.getBankAccountId());
        List<AllocationDto> allocs = p.getAllocations().stream()
                .map(a -> new AllocationDto(
                        a.getId(), a.getInvoiceId(), a.getExpenseId(),
                        a.getAllocationDate(),
                        MoneyUtil.toMoneyString(a.getAllocatedAmount()),
                        MoneyUtil.toMoneyString(a.getBaseAllocatedAmount()),
                        MoneyUtil.toMoneyString(a.getRealizedFxGainLoss()),
                        a.isReversed()
                ))
                .toList();

        return new PaymentDto(
                p.getId(), p.getPaymentNumber(), p.getPaymentType().name(),
                p.getContactId(), contact.getName(), p.getPaymentDate(),
                p.getPaymentMethod().name(), p.getBankAccountId(), bank.getName(),
                p.getCurrencyCode(), p.getExchangeRate().stripTrailingZeros().toPlainString(),
                MoneyUtil.toMoneyString(p.getAmount()),
                MoneyUtil.toMoneyString(p.getBaseAmount()),
                MoneyUtil.toMoneyString(p.getAllocatedAmount()),
                MoneyUtil.toMoneyString(p.getUnallocatedAmount()),
                p.getReferenceNumber(), p.getNotes(), p.getStatus().name(),
                p.getJournalEntryId(), p.getCreatedAt(), allocs
        );
    }

    public record AllocationInput(UUID invoiceId, UUID expenseId, @jakarta.validation.constraints.NotNull BigDecimal amount) {}

    public record RecordPaymentRequest(
            @jakarta.validation.constraints.NotNull PaymentType paymentType,
            @jakarta.validation.constraints.NotNull UUID contactId,
            LocalDate paymentDate,
            PaymentMethod paymentMethod,
            UUID bankAccountId,
            String currencyCode,
            BigDecimal exchangeRate,
            @jakarta.validation.constraints.NotNull BigDecimal amount,
            String referenceNumber,
            String notes,
            String idempotencyKey,
            List<AllocationInput> allocations
    ) {}

    public record AllocationDto(
            UUID id,
            UUID invoiceId,
            UUID expenseId,
            LocalDate allocationDate,
            String allocatedAmount,
            String baseAllocatedAmount,
            String realizedFxGainLoss,
            boolean reversed
    ) {}

    public record PaymentDto(
            UUID id,
            String paymentNumber,
            String paymentType,
            UUID contactId,
            String contactName,
            LocalDate paymentDate,
            String paymentMethod,
            UUID bankAccountId,
            String bankAccountName,
            String currencyCode,
            String exchangeRate,
            String amount,
            String baseAmount,
            String allocatedAmount,
            String unallocatedAmount,
            String referenceNumber,
            String notes,
            String status,
            UUID journalEntryId,
            Instant createdAt,
            List<AllocationDto> allocations
    ) {}
}
