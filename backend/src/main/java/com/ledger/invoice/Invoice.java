package com.ledger.invoice;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "invoice_number", nullable = false, length = 64)
    private String invoiceNumber;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private InvoiceStatus status;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Column(name = "exchange_rate_date")
    private LocalDate exchangeRateDate;

    @Column(name = "tax_inclusive", nullable = false)
    private boolean taxInclusive;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "discount_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountTotal;

    @Column(name = "tax_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxTotal;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "base_total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseTotalAmount;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountPaid;

    @Column(name = "credit_applied", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditApplied;

    @Column(name = "outstanding_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingBalance;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "payment_instructions", columnDefinition = "TEXT")
    private String paymentInstructions;

    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<InvoiceLine> lines = new ArrayList<>();

    protected Invoice() {
    }

    public Invoice(UUID organizationId, String invoiceNumber, UUID customerId, LocalDate issueDate, LocalDate dueDate,
                   int paymentTermsDays, String currencyCode, BigDecimal exchangeRate, boolean taxInclusive,
                   String notes, String paymentInstructions, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.invoiceNumber = invoiceNumber;
        this.customerId = customerId;
        this.status = InvoiceStatus.DRAFT;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.paymentTermsDays = paymentTermsDays;
        this.currencyCode = currencyCode;
        this.exchangeRate = exchangeRate;
        this.exchangeRateDate = issueDate;
        this.taxInclusive = taxInclusive;
        this.subtotal = BigDecimal.ZERO;
        this.discountTotal = BigDecimal.ZERO;
        this.taxTotal = BigDecimal.ZERO;
        this.totalAmount = BigDecimal.ZERO;
        this.baseTotalAmount = BigDecimal.ZERO;
        this.amountPaid = BigDecimal.ZERO;
        this.creditApplied = BigDecimal.ZERO;
        this.outstandingBalance = BigDecimal.ZERO;
        this.notes = notes;
        this.paymentInstructions = paymentInstructions;
        this.createdBy = createdBy;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addLine(InvoiceLine line) {
        line.attachTo(this);
        this.lines.add(line);
    }

    public void clearLines() {
        this.lines.clear();
    }

    public void applyComputedTotals(BigDecimal subtotal, BigDecimal discountTotal, BigDecimal taxTotal,
                                    BigDecimal totalAmount, BigDecimal baseTotalAmount) {
        this.subtotal = subtotal;
        this.discountTotal = discountTotal;
        this.taxTotal = taxTotal;
        this.totalAmount = totalAmount;
        this.baseTotalAmount = baseTotalAmount;
        recalculateSettlementState();
    }

    public void applyPayment(BigDecimal amount) {
        this.amountPaid = this.amountPaid.add(amount);
        recalculateSettlementState();
    }

    public void reversePayment(BigDecimal amount) {
        this.amountPaid = this.amountPaid.subtract(amount).max(BigDecimal.ZERO);
        recalculateSettlementState();
    }

    public void applyCredit(BigDecimal amount) {
        this.creditApplied = this.creditApplied.add(amount);
        recalculateSettlementState();
    }

    private void recalculateSettlementState() {
        this.outstandingBalance = this.totalAmount.subtract(this.amountPaid).subtract(this.creditApplied);
        if (this.outstandingBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Invoice outstanding balance cannot become negative");
        }
        if (this.status == InvoiceStatus.ISSUED || this.status == InvoiceStatus.PARTIALLY_PAID || this.status == InvoiceStatus.PAID) {
            if (this.outstandingBalance.compareTo(BigDecimal.ZERO) == 0) {
                this.status = InvoiceStatus.PAID;
            } else if (this.amountPaid.add(this.creditApplied).compareTo(BigDecimal.ZERO) > 0) {
                this.status = InvoiceStatus.PARTIALLY_PAID;
            } else {
                this.status = InvoiceStatus.ISSUED;
            }
        }
        this.updatedAt = Instant.now();
    }

    public void markIssued(String finalInvoiceNumber, UUID journalEntryId) {
        this.invoiceNumber = finalInvoiceNumber;
        this.journalEntryId = journalEntryId;
        this.status = InvoiceStatus.ISSUED;
        this.issuedAt = Instant.now();
        recalculateSettlementState();
    }

    public void markVoid() {
        this.status = InvoiceStatus.VOID;
        this.outstandingBalance = BigDecimal.ZERO;
        this.voidedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isOverdueOn(LocalDate asOfDate) {
        return (status == InvoiceStatus.ISSUED || status == InvoiceStatus.PARTIALLY_PAID)
                && outstandingBalance.compareTo(BigDecimal.ZERO) > 0
                && dueDate.isBefore(asOfDate);
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; this.exchangeRateDate = issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public int getPaymentTermsDays() { return paymentTermsDays; }
    public void setPaymentTermsDays(int paymentTermsDays) { this.paymentTermsDays = paymentTermsDays; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }
    public LocalDate getExchangeRateDate() { return exchangeRateDate; }
    public boolean isTaxInclusive() { return taxInclusive; }
    public void setTaxInclusive(boolean taxInclusive) { this.taxInclusive = taxInclusive; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public BigDecimal getTaxTotal() { return taxTotal; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getBaseTotalAmount() { return baseTotalAmount; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public BigDecimal getCreditApplied() { return creditApplied; }
    public BigDecimal getOutstandingBalance() { return outstandingBalance; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getPaymentInstructions() { return paymentInstructions; }
    public void setPaymentInstructions(String paymentInstructions) { this.paymentInstructions = paymentInstructions; }
    public UUID getJournalEntryId() { return journalEntryId; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getVoidedAt() { return voidedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public List<InvoiceLine> getLines() { return Collections.unmodifiableList(lines); }
}
