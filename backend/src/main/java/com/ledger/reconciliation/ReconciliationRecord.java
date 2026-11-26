package com.ledger.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_records")
public class ReconciliationRecord {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "bank_account_id", nullable = false)
    private UUID bankAccountId;

    @Column(name = "bank_statement_line_id", nullable = false)
    private UUID bankStatementLineId;

    @Column(name = "journal_line_id", nullable = false)
    private UUID journalLineId;

    @Column(name = "active_bank_line_id", unique = true)
    private UUID activeBankLineId;

    @Column(name = "active_journal_line_id", unique = true)
    private UUID activeJournalLineId;

    @Column(name = "matched_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal matchedAmount;

    @Column(name = "match_type", nullable = false, length = 24)
    private String matchType;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 500)
    private String notes;

    @Column(name = "reconciled_by")
    private UUID reconciledBy;

    @Column(name = "reconciled_at", nullable = false)
    private Instant reconciledAt;

    @Column(name = "unmatched_by")
    private UUID unmatchedBy;

    @Column(name = "unmatched_at")
    private Instant unmatchedAt;

    protected ReconciliationRecord() {
    }

    public ReconciliationRecord(UUID organizationId, UUID bankAccountId, UUID bankStatementLineId,
                                UUID journalLineId, BigDecimal matchedAmount, String matchType,
                                String notes, UUID reconciledBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.bankAccountId = bankAccountId;
        this.bankStatementLineId = bankStatementLineId;
        this.journalLineId = journalLineId;
        this.activeBankLineId = bankStatementLineId;
        this.activeJournalLineId = journalLineId;
        this.matchedAmount = matchedAmount;
        this.matchType = matchType;
        this.status = "MATCHED";
        this.notes = notes;
        this.reconciledBy = reconciledBy;
        this.reconciledAt = Instant.now();
    }

    public void unmatch(UUID actorId) {
        this.status = "UNMATCHED";
        this.activeBankLineId = null;
        this.activeJournalLineId = null;
        this.unmatchedBy = actorId;
        this.unmatchedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getBankAccountId() { return bankAccountId; }
    public UUID getBankStatementLineId() { return bankStatementLineId; }
    public UUID getJournalLineId() { return journalLineId; }
    public BigDecimal getMatchedAmount() { return matchedAmount; }
    public String getMatchType() { return matchType; }
    public String getStatus() { return status; }
    public String getNotes() { return notes; }
    public UUID getReconciledBy() { return reconciledBy; }
    public Instant getReconciledAt() { return reconciledAt; }
    public Instant getUnmatchedAt() { return unmatchedAt; }
}
