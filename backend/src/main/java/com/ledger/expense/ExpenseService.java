package com.ledger.expense;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final AccountService accountService;
    private final ContactService contactService;
    private final OrganizationService organizationService;
    private final JournalService journalService;
    private final DocumentSequenceService sequenceService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            ExpenseCategoryRepository categoryRepository,
            AccountService accountService,
            ContactService contactService,
            OrganizationService organizationService,
            JournalService journalService,
            DocumentSequenceService sequenceService,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.accountService = accountService;
        this.contactService = contactService;
        this.organizationService = organizationService;
        this.journalService = journalService;
        this.sequenceService = sequenceService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExpenseDto createExpense(LedgerPrincipal principal, CreateExpenseRequest req) {
        principal.requireFinanceOperations();
        UUID orgId = principal.requireOrganizationId();
        Organization org = organizationService.getOrganizationEntity(orgId);
        FinancialSettings settings = organizationService.getSettingsEntity(orgId);

        ExpenseType type = req.expenseType() != null ? req.expenseType() : ExpenseType.IMMEDIATE_EXPENSE;
        LocalDate expDate = req.expenseDate() != null ? req.expenseDate() : LocalDate.now();
        LocalDate dueDate = type == ExpenseType.SUPPLIER_BILL
                ? (req.dueDate() != null ? req.dueDate() : expDate.plusDays(settings.getDefaultPaymentTermsDays()))
                : null;

        UUID supplierId = null;
        if (req.supplierId() != null) {
            Contact supplier = contactService.requireContact(orgId, req.supplierId());
            if (!supplier.isSupplier()) {
                throw LedgerException.businessRule("CONTACT_NOT_SUPPLIER", "Selected contact is not a supplier");
            }
            supplierId = supplier.getId();
        } else if (type == ExpenseType.SUPPLIER_BILL) {
            throw LedgerException.badRequest("SUPPLIER_REQUIRED", "Supplier is required for a supplier bill");
        }

        UUID expenseAccountId = req.expenseAccountId();
        if (expenseAccountId == null && req.categoryId() != null) {
            ExpenseCategory cat = categoryRepository.findByIdAndOrganizationId(req.categoryId(), orgId)
                    .orElseThrow(() -> LedgerException.notFound("Expense category not found"));
            expenseAccountId = cat.getDefaultAccountId();
        }
        if (expenseAccountId == null) {
            expenseAccountId = accountService.requireBySystemCode(orgId, "OPERATING_EXPENSE").getId();
        }
        Account expAccount = accountService.requirePostingAccount(orgId, expenseAccountId);
        if (expAccount.getAccountType() != AccountType.EXPENSE && expAccount.getAccountType() != AccountType.ASSET) {
            throw LedgerException.businessRule("INVALID_EXPENSE_ACCOUNT", "Expense or bill must post to an Expense or Asset account");
        }

        UUID paymentAccountId = null;
        if (type == ExpenseType.IMMEDIATE_EXPENSE) {
            Account payAcc = req.paymentAccountId() != null
                    ? accountService.requirePostingAccount(orgId, req.paymentAccountId())
                    : accountService.requireBySystemCode(orgId, "BANK");
            if (payAcc.getAccountType() != AccountType.ASSET) {
                throw LedgerException.businessRule("INVALID_PAYMENT_ACCOUNT", "Payment account must be a Cash or Bank asset account");
            }
            paymentAccountId = payAcc.getId();
        }

        String ccy = (req.currencyCode() == null || req.currencyCode().isBlank())
                ? org.getBaseCurrency() : req.currencyCode().toUpperCase(Locale.ROOT);
        BigDecimal fxRate = organizationService.resolveExchangeRate(orgId, ccy, expDate, req.exchangeRate());

        if (req.netAmount() == null || !MoneyUtil.isPositive(req.netAmount())) {
            throw LedgerException.badRequest("INVALID_AMOUNT", "Expense net amount must be positive");
        }
        BigDecimal netAmount = MoneyUtil.round(req.netAmount(), ccy);
        BigDecimal taxRate = req.taxRate() != null ? req.taxRate() : BigDecimal.ZERO;
        if (MoneyUtil.isNegative(taxRate)) {
            throw LedgerException.badRequest("INVALID_TAX_RATE", "Tax rate cannot be negative");
        }
        BigDecimal taxAmount = req.taxAmount() != null
                ? MoneyUtil.round(req.taxAmount(), ccy)
                : MoneyUtil.percentageOf(netAmount, taxRate, ccy);
        BigDecimal totalAmount = MoneyUtil.round(netAmount.add(taxAmount), ccy);
        BigDecimal baseTotal = MoneyUtil.convertToBase(totalAmount, fxRate, org.getBaseCurrency());

        String prefix = type == ExpenseType.SUPPLIER_BILL ? "BILL" : settings.getExpensePrefix();
        String expNumber = sequenceService.nextNumber(orgId, type == ExpenseType.SUPPLIER_BILL ? "SUPPLIER_BILL" : "EXPENSE", prefix, expDate.getYear());

        Expense expense = new Expense(
                orgId, expNumber, type, req.title(), expDate, dueDate, supplierId, req.categoryId(),
                expAccount.getId(), paymentAccountId, ccy, fxRate, netAmount, taxRate,
                taxAmount, totalAmount, baseTotal, req.notes(), principal.userId()
        );
        expenseRepository.save(expense);

        auditService.record(orgId, "EXPENSE_CREATED", "EXPENSE", expense.getId().toString(),
                "Created " + type + " " + expNumber + " (" + ccy + " " + MoneyUtil.toMoneyString(totalAmount) + ")", Map.of());

        if (Boolean.TRUE.equals(req.postImmediately())) {
            return postExpense(principal, expense.getId());
        }
        return toDto(expense);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExpenseDto postExpense(LedgerPrincipal principal, UUID expenseId) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();

        Expense expense = expenseRepository.findByIdAndOrganizationIdForUpdate(expenseId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Expense not found"));

        if (expense.getStatus() == ExpenseStatus.POSTED) {
            throw LedgerException.businessRule("ALREADY_POSTED", "Expense is already posted");
        }
        if (expense.getStatus() == ExpenseStatus.REVERSED || expense.getStatus() == ExpenseStatus.REJECTED) {
            throw LedgerException.businessRule("INVALID_STATUS", "Rejected or reversed expense cannot be posted");
        }

        FinancialSettings settings = organizationService.getSettingsEntity(orgId);
        boolean needsApproval = settings.isRequireExpenseApproval()
                && expense.getBaseTotalAmount().compareTo(settings.getExpenseApprovalThreshold()) >= 0;
        if (needsApproval && expense.getStatus() != ExpenseStatus.APPROVED) {
            throw LedgerException.businessRule(
                    "EXPENSE_APPROVAL_REQUIRED",
                    "Expense " + expense.getExpenseNumber() + " exceeds approval threshold and must be approved before posting"
            );
        }

        Organization org = organizationService.getOrganizationEntity(orgId);
        BigDecimal baseTotal = MoneyUtil.convertToBase(expense.getTotalAmount(), expense.getExchangeRate(), org.getBaseCurrency());
        BigDecimal baseTax = MoneyUtil.isPositive(expense.getTaxAmount())
                ? MoneyUtil.convertToBase(expense.getTaxAmount(), expense.getExchangeRate(), org.getBaseCurrency())
                : MoneyUtil.ZERO;
        BigDecimal baseNet = baseTotal.subtract(baseTax);

        List<JournalService.JournalLineInput> lines = new ArrayList<>();
        lines.add(JournalService.JournalLineInput.baseDebit(
                expense.getExpenseAccountId(), expense.getSupplierId(),
                expense.getTitle() + " (" + expense.getExpenseNumber() + ")",
                expense.getNetAmount(), baseNet
        ));
        if (MoneyUtil.isPositive(baseTax)) {
            Account taxRecv = accountService.requireBySystemCode(orgId, "TAX_RECEIVABLE");
            lines.add(JournalService.JournalLineInput.baseDebit(
                    taxRecv.getId(), expense.getSupplierId(),
                    "Input tax for " + expense.getExpenseNumber(),
                    expense.getTaxAmount(), baseTax
            ));
        }

        UUID creditAccountId = expense.getExpenseType() == ExpenseType.SUPPLIER_BILL
                ? accountService.requireBySystemCode(orgId, "AP").getId()
                : expense.getPaymentAccountId();

        lines.add(JournalService.JournalLineInput.baseCredit(
                creditAccountId, expense.getSupplierId(),
                expense.getExpenseType() == ExpenseType.SUPPLIER_BILL
                        ? "Accounts Payable for bill " + expense.getExpenseNumber()
                        : "Cash/Bank disbursement for " + expense.getExpenseNumber(),
                expense.getTotalAmount(), baseTotal
        ));

        JournalSourceType sourceType = expense.getExpenseType() == ExpenseType.SUPPLIER_BILL
                ? JournalSourceType.SUPPLIER_BILL : JournalSourceType.EXPENSE;

        JournalEntry journal = journalService.postAutomatedJournal(
                orgId, principal.userId(), expense.getExpenseDate(),
                (expense.getExpenseType() == ExpenseType.SUPPLIER_BILL ? "Supplier bill " : "Expense ")
                        + expense.getExpenseNumber() + " - " + expense.getTitle(),
                expense.getExpenseNumber(), sourceType, expense.getId(),
                "EXPENSE_POST:" + expense.getId(),
                expense.getCurrencyCode(), expense.getExchangeRate(), lines
        );

        expense.markPosted(journal.getId());
        expenseRepository.save(expense);

        auditService.record(orgId, "EXPENSE_POSTED", "EXPENSE", expense.getId().toString(),
                "Posted " + expense.getExpenseType() + " " + expense.getExpenseNumber(),
                Map.of("journalEntryId", journal.getId().toString()));
        eventPublisher.publishEvent(new DomainEvents.ExpensePostedEvent(
                orgId, expense.getId(), expense.getExpenseNumber(), expense.getExpenseType().name(),
                expense.getTotalAmount(), expense.getCurrencyCode()));
        return toDto(expense);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExpenseDto reverseExpense(LedgerPrincipal principal, UUID expenseId, String reason) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();

        Expense expense = expenseRepository.findByIdAndOrganizationIdForUpdate(expenseId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Expense not found"));
        if (expense.getStatus() != ExpenseStatus.POSTED) {
            throw LedgerException.businessRule("ONLY_POSTED_REVERSIBLE", "Only posted expenses can be reversed");
        }
        if (expense.getExpenseType() == ExpenseType.SUPPLIER_BILL && MoneyUtil.isPositive(expense.getAmountPaid())) {
            throw LedgerException.businessRule("BILL_HAS_PAYMENTS", "Reverse supplier payments before reversing this bill");
        }

        journalService.reverseJournalInternal(
                orgId, principal.userId(), expense.getJournalEntryId(),
                LocalDate.now(), "Reversal of " + expense.getExpenseNumber() + (reason != null ? ": " + reason : "")
        );
        expense.markReversed();
        expenseRepository.save(expense);

        auditService.record(orgId, "EXPENSE_REVERSED", "EXPENSE", expense.getId().toString(),
                "Reversed " + expense.getExpenseNumber(), Map.of());
        return toDto(expense);
    }

    @Transactional(readOnly = true)
    public List<ExpenseDto> listExpenses(UUID organizationId, ExpenseType type, ExpenseStatus status) {
        return expenseRepository.findByOrganizationIdOrderByExpenseDateDescCreatedAtDesc(organizationId).stream()
                .filter(e -> type == null || e.getExpenseType() == type)
                .filter(e -> status == null || e.getStatus() == status)
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExpenseDto getExpense(UUID organizationId, UUID expenseId) {
        Expense e = expenseRepository.findByIdAndOrganizationId(expenseId, organizationId)
                .orElseThrow(() -> LedgerException.notFound("Expense not found"));
        return toDto(e);
    }

    @Transactional(readOnly = true)
    public List<ExpenseCategoryDto> listCategories(UUID organizationId) {
        return categoryRepository.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .map(c -> {
                    Account a = accountService.requireAccount(organizationId, c.getDefaultAccountId());
                    return new ExpenseCategoryDto(c.getId(), c.getName(), c.getDefaultAccountId(), a.getCode(), a.getName(), c.isActive());
                })
                .toList();
    }

    @Transactional
    public ExpenseCategoryDto createCategory(LedgerPrincipal principal, String name, UUID defaultAccountId) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();
        Account acc = accountService.requirePostingAccount(orgId, defaultAccountId);
        ExpenseCategory cat = new ExpenseCategory(orgId, name, acc.getId());
        categoryRepository.save(cat);
        return new ExpenseCategoryDto(cat.getId(), cat.getName(), cat.getDefaultAccountId(), acc.getCode(), acc.getName(), cat.isActive());
    }

    public ExpenseDto toDto(Expense e) {
        Account expAcc = accountService.requireAccount(e.getOrganizationId(), e.getExpenseAccountId());
        Account payAcc = e.getPaymentAccountId() != null
                ? accountService.requireAccount(e.getOrganizationId(), e.getPaymentAccountId())
                : null;
        String supplierName = e.getSupplierId() != null
                ? contactService.requireContact(e.getOrganizationId(), e.getSupplierId()).getName()
                : null;
        String categoryName = e.getCategoryId() != null
                ? categoryRepository.findByIdAndOrganizationId(e.getCategoryId(), e.getOrganizationId())
                        .map(ExpenseCategory::getName).orElse(null)
                : null;

        boolean overdue = e.getExpenseType() == ExpenseType.SUPPLIER_BILL
                && e.getStatus() == ExpenseStatus.POSTED
                && MoneyUtil.isPositive(e.getOutstandingBalance())
                && e.getDueDate() != null
                && e.getDueDate().isBefore(LocalDate.now());

        return new ExpenseDto(
                e.getId(), e.getExpenseNumber(), e.getExpenseType().name(), e.getTitle(),
                e.getExpenseDate(), e.getDueDate(), overdue,
                e.getSupplierId(), supplierName, e.getCategoryId(), categoryName,
                e.getExpenseAccountId(), expAcc.getCode(), expAcc.getName(),
                e.getPaymentAccountId(), payAcc != null ? payAcc.getName() : null,
                e.getCurrencyCode(), e.getExchangeRate().stripTrailingZeros().toPlainString(),
                MoneyUtil.toMoneyString(e.getNetAmount()),
                e.getTaxRate().stripTrailingZeros().toPlainString(),
                MoneyUtil.toMoneyString(e.getTaxAmount()),
                MoneyUtil.toMoneyString(e.getTotalAmount()),
                MoneyUtil.toMoneyString(e.getBaseTotalAmount()),
                MoneyUtil.toMoneyString(e.getAmountPaid()),
                MoneyUtil.toMoneyString(e.getOutstandingBalance()),
                e.getStatus().name(), e.getNotes(), e.getJournalEntryId(),
                e.getCreatedBy(), e.getApprovedBy(), e.getPostedAt(), e.getCreatedAt()
        );
    }

    public record CreateExpenseRequest(
            ExpenseType expenseType,
            @jakarta.validation.constraints.NotBlank String title,
            LocalDate expenseDate,
            LocalDate dueDate,
            UUID supplierId,
            UUID categoryId,
            UUID expenseAccountId,
            UUID paymentAccountId,
            String currencyCode,
            BigDecimal exchangeRate,
            @jakarta.validation.constraints.NotNull BigDecimal netAmount,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            String notes,
            Boolean postImmediately
    ) {}

    public record ExpenseCategoryDto(
            UUID id,
            String name,
            UUID defaultAccountId,
            String accountCode,
            String accountName,
            boolean active
    ) {}

    public record ExpenseDto(
            UUID id,
            String expenseNumber,
            String expenseType,
            String title,
            LocalDate expenseDate,
            LocalDate dueDate,
            boolean overdue,
            UUID supplierId,
            String supplierName,
            UUID categoryId,
            String categoryName,
            UUID expenseAccountId,
            String expenseAccountCode,
            String expenseAccountName,
            UUID paymentAccountId,
            String paymentAccountName,
            String currencyCode,
            String exchangeRate,
            String netAmount,
            String taxRate,
            String taxAmount,
            String totalAmount,
            String baseTotalAmount,
            String amountPaid,
            String outstandingBalance,
            String status,
            String notes,
            UUID journalEntryId,
            UUID createdBy,
            UUID approvedBy,
            Instant postedAt,
            Instant createdAt
    ) {}
}
