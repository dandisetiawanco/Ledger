package com.ledger.payment;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "payment_number", nullable = false, length = 64)
    private String paymentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 24)
    private PaymentType paymentType;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private PaymentMethod paymentMethod;

    @Column(name = "bank_account_id", nullable = false)
    private UUID bankAccountId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Column(name = "exchange_rate_date")
    private LocalDate exchangeRateDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "base_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseAmount;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount;

    @Column(name = "unallocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal unallocatedAmount;

    @Column(name = "reference_number", length = 120)
    private String referenceNumber;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentStatus status;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(name = "reversal_journal_entry_id")
    private UUID reversalJournalEntryId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PaymentAllocation> allocations = new ArrayList<>();

    protected Payment() {
    }

    public Payment(UUID organizationId, String paymentNumber, PaymentType paymentType, UUID contactId,
                   LocalDate paymentDate, PaymentMethod paymentMethod, UUID bankAccountId,
                   String currencyCode, BigDecimal exchangeRate, BigDecimal amount, BigDecimal baseAmount,
                   BigDecimal allocatedAmount, BigDecimal unallocatedAmount, String referenceNumber,
                   String notes, String idempotencyKey, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.paymentNumber = paymentNumber;
        this.paymentType = paymentType;
        this.contactId = contactId;
        this.paymentDate = paymentDate;
        this.paymentMethod = paymentMethod;
        this.bankAccountId = bankAccountId;
        this.currencyCode = currencyCode;
        this.exchangeRate = exchangeRate;
        this.exchangeRateDate = paymentDate;
        this.amount = amount;
        this.baseAmount = baseAmount;
        this.allocatedAmount = allocatedAmount;
        this.unallocatedAmount = unallocatedAmount;
        this.referenceNumber = referenceNumber;
        this.notes = notes;
        this.status = PaymentStatus.POSTED;
        this.idempotencyKey = idempotencyKey;
        this.createdBy = createdBy;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addAllocation(PaymentAllocation allocation) {
        allocation.attachTo(this);
        this.allocations.add(allocation);
    }

    public void attachJournal(UUID journalEntryId) {
        this.journalEntryId = journalEntryId;
        this.updatedAt = Instant.now();
    }

    public void markReversed(UUID reversalJournalId) {
        this.status = PaymentStatus.REVERSED;
        this.reversalJournalEntryId = reversalJournalId;
        for (PaymentAllocation alloc : allocations) {
            alloc.markReversed();
        }
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getPaymentNumber() { return paymentNumber; }
    public PaymentType getPaymentType() { return paymentType; }
    public UUID getContactId() { return contactId; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public UUID getBankAccountId() { return bankAccountId; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public BigDecimal getUnallocatedAmount() { return unallocatedAmount; }
    public String getReferenceNumber() { return referenceNumber; }
    public String getNotes() { return notes; }
    public PaymentStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getJournalEntryId() { return journalEntryId; }
    public UUID getReversalJournalEntryId() { return reversalJournalEntryId; }
    public Instant getCreatedAt() { return createdAt; }
    public List<PaymentAllocation> getAllocations() { return Collections.unmodifiableList(allocations); }
}
