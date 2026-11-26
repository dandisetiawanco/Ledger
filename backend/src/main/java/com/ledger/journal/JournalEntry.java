package com.ledger.journal;

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
@Table(name = "journal_entries")
public class JournalEntry {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "entry_number", nullable = false, length = 64)
    private String entryNumber;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "accounting_period_id", nullable = false)
    private UUID accountingPeriodId;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 120)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private JournalStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private JournalSourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "business_event_key", length = 160)
    private String businessEventKey;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Column(name = "exchange_rate_date")
    private LocalDate exchangeRateDate;

    @Column(name = "total_debit", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDebit;

    @Column(name = "total_credit", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCredit;

    @Column(name = "reversed_entry_id")
    private UUID reversedEntryId;

    @Column(name = "reversal_entry_id")
    private UUID reversalEntryId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "posted_by")
    private UUID postedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<JournalLine> lines = new ArrayList<>();

    protected JournalEntry() {
    }

    public JournalEntry(UUID organizationId, String entryNumber, LocalDate entryDate, UUID accountingPeriodId,
                        String description, String reference, JournalStatus status, JournalSourceType sourceType,
                        UUID sourceId, String businessEventKey, String currencyCode, BigDecimal exchangeRate,
                        LocalDate exchangeRateDate, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.entryNumber = entryNumber;
        this.entryDate = entryDate;
        this.accountingPeriodId = accountingPeriodId;
        this.description = description;
        this.reference = reference;
        this.status = status;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.businessEventKey = businessEventKey;
        this.currencyCode = currencyCode;
        this.exchangeRate = exchangeRate;
        this.exchangeRateDate = exchangeRateDate;
        this.totalDebit = BigDecimal.ZERO;
        this.totalCredit = BigDecimal.ZERO;
        this.createdBy = createdBy;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addLine(JournalLine line) {
        line.attachTo(this);
        this.lines.add(line);
    }

    public void clearLines() {
        this.lines.clear();
    }

    public void setTotals(BigDecimal totalDebit, BigDecimal totalCredit) {
        this.totalDebit = totalDebit;
        this.totalCredit = totalCredit;
        this.updatedAt = Instant.now();
    }

    public void markPosted(UUID actorId) {
        this.status = JournalStatus.POSTED;
        this.postedBy = actorId;
        this.postedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markApproved(UUID actorId) {
        this.approvedBy = actorId;
        this.approvedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markReversed(UUID reversalId) {
        this.status = JournalStatus.REVERSED;
        this.reversalEntryId = reversalId;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getEntryNumber() { return entryNumber; }
    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; this.updatedAt = Instant.now(); }
    public UUID getAccountingPeriodId() { return accountingPeriodId; }
    public void setAccountingPeriodId(UUID accountingPeriodId) { this.accountingPeriodId = accountingPeriodId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; this.updatedAt = Instant.now(); }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; this.updatedAt = Instant.now(); }
    public JournalStatus getStatus() { return status; }
    public void setStatus(JournalStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public JournalSourceType getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public String getBusinessEventKey() { return businessEventKey; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public LocalDate getExchangeRateDate() { return exchangeRateDate; }
    public BigDecimal getTotalDebit() { return totalDebit; }
    public BigDecimal getTotalCredit() { return totalCredit; }
    public UUID getReversedEntryId() { return reversedEntryId; }
    public void setReversedEntryId(UUID reversedEntryId) { this.reversedEntryId = reversedEntryId; }
    public UUID getReversalEntryId() { return reversalEntryId; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public UUID getPostedBy() { return postedBy; }
    public Instant getPostedAt() { return postedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public List<JournalLine> getLines() { return Collections.unmodifiableList(lines); }
}
