package com.ledger.expense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "expense_number", nullable = false, length = 64)
    private String expenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_type", nullable = false, length = 24)
    private ExpenseType expenseType;

    @Column(nullable = false, length = 250)
    private String title;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "expense_account_id", nullable = false)
    private UUID expenseAccountId;

    @Column(name = "payment_account_id")
    private UUID paymentAccountId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Column(name = "exchange_rate_date")
    private LocalDate exchangeRateDate;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "tax_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "base_total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseTotalAmount;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountPaid;

    @Column(name = "outstanding_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExpenseStatus status;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Expense() {
    }

    public Expense(UUID organizationId, String expenseNumber, ExpenseType expenseType, String title,
                   LocalDate expenseDate, LocalDate dueDate, UUID supplierId, UUID categoryId,
                   UUID expenseAccountId, UUID paymentAccountId, String currencyCode,
                   BigDecimal exchangeRate, BigDecimal netAmount, BigDecimal taxRate,
                   BigDecimal taxAmount, BigDecimal totalAmount, BigDecimal baseTotalAmount,
                   String notes, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.expenseNumber = expenseNumber;
        this.expenseType = expenseType;
        this.title = title.trim();
        this.expenseDate = expenseDate;
        this.dueDate = dueDate;
        this.supplierId = supplierId;
        this.categoryId = categoryId;
        this.expenseAccountId = expenseAccountId;
        this.paymentAccountId = paymentAccountId;
        this.currencyCode = currencyCode;
        this.exchangeRate = exchangeRate;
        this.exchangeRateDate = expenseDate;
        this.netAmount = netAmount;
        this.taxRate = taxRate;
        this.taxAmount = taxAmount;
        this.totalAmount = totalAmount;
        this.baseTotalAmount = baseTotalAmount;
        this.amountPaid = expenseType == ExpenseType.IMMEDIATE_EXPENSE ? totalAmount : BigDecimal.ZERO;
        this.outstandingBalance = expenseType == ExpenseType.IMMEDIATE_EXPENSE ? BigDecimal.ZERO : totalAmount;
        this.status = ExpenseStatus.DRAFT;
        this.notes = notes;
        this.createdBy = createdBy;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void applyBillPayment(BigDecimal amount) {
        this.amountPaid = this.amountPaid.add(amount);
        this.outstandingBalance = this.totalAmount.subtract(this.amountPaid);
        if (this.outstandingBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Supplier bill outstanding balance cannot be negative");
        }
        this.updatedAt = Instant.now();
    }

    public void reverseBillPayment(BigDecimal amount) {
        this.amountPaid = this.amountPaid.subtract(amount).max(BigDecimal.ZERO);
        this.outstandingBalance = this.totalAmount.subtract(this.amountPaid);
        this.updatedAt = Instant.now();
    }

    public void markApproved(UUID approverId) {
        this.status = ExpenseStatus.APPROVED;
        this.approvedBy = approverId;
        this.approvedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markPosted(UUID journalEntryId) {
        this.journalEntryId = journalEntryId;
        this.status = ExpenseStatus.POSTED;
        this.postedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markReversed() {
        this.status = ExpenseStatus.REVERSED;
        this.outstandingBalance = BigDecimal.ZERO;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getExpenseNumber() { return expenseNumber; }
    public ExpenseType getExpenseType() { return expenseType; }
    public String getTitle() { return title; }
    public LocalDate getExpenseDate() { return expenseDate; }
    public LocalDate getDueDate() { return dueDate; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getCategoryId() { return categoryId; }
    public UUID getExpenseAccountId() { return expenseAccountId; }
    public UUID getPaymentAccountId() { return paymentAccountId; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getBaseTotalAmount() { return baseTotalAmount; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public BigDecimal getOutstandingBalance() { return outstandingBalance; }
    public ExpenseStatus getStatus() { return status; }
    public void setStatus(ExpenseStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public String getNotes() { return notes; }
    public UUID getJournalEntryId() { return journalEntryId; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getPostedAt() { return postedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
