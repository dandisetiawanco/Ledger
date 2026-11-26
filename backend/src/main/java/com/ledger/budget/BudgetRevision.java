package com.ledger.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "budget_revisions")
public class BudgetRevision {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "change_summary", nullable = false, columnDefinition = "TEXT")
    private String changeSummary;

    @Column(name = "revised_by")
    private UUID revisedBy;

    @Column(name = "revised_at", nullable = false)
    private Instant revisedAt;

    protected BudgetRevision() {
    }

    public BudgetRevision(UUID organizationId, UUID budgetId, int revisionNumber, String changeSummary, UUID revisedBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.budgetId = budgetId;
        this.revisionNumber = revisionNumber;
        this.changeSummary = changeSummary;
        this.revisedBy = revisedBy;
        this.revisedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public int getRevisionNumber() { return revisionNumber; }
    public String getChangeSummary() { return changeSummary; }
    public UUID getRevisedBy() { return revisedBy; }
    public Instant getRevisedAt() { return revisedAt; }
}
