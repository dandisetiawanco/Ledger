package com.ledger.budget;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "fiscal_year_id")
    private UUID fiscalYearId;

    @Column(name = "period_type", nullable = false, length = 16)
    private String periodType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BudgetLine> lines = new ArrayList<>();

    protected Budget() {
    }

    public Budget(UUID organizationId, String name, UUID fiscalYearId, String periodType,
                  LocalDate startDate, LocalDate endDate, String currencyCode, String notes, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.name = name.trim();
        this.fiscalYearId = fiscalYearId;
        this.periodType = periodType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.currencyCode = currencyCode;
        this.notes = notes;
        this.revisionNumber = 1;
        this.active = true;
        this.createdBy = createdBy;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addLine(BudgetLine line) {
        line.attachTo(this);
        this.lines.add(line);
    }

    public void clearLines() {
        this.lines.clear();
    }

    public void incrementRevision() {
        this.revisionNumber++;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name.trim(); this.updatedAt = Instant.now(); }
    public UUID getFiscalYearId() { return fiscalYearId; }
    public String getPeriodType() { return periodType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getCurrencyCode() { return currencyCode; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; this.updatedAt = Instant.now(); }
    public int getRevisionNumber() { return revisionNumber; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public List<BudgetLine> getLines() { return Collections.unmodifiableList(lines); }
}
