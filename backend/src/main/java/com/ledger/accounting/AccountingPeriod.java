package com.ledger.accounting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "accounting_periods")
public class AccountingPeriod {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "fiscal_year_id", nullable = false)
    private UUID fiscalYearId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "period_number", nullable = false)
    private int periodNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PeriodStatus status;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "reopened_at")
    private Instant reopenedAt;

    @Column(name = "reopened_by")
    private UUID reopenedBy;

    @Column(name = "reopen_reason", columnDefinition = "TEXT")
    private String reopenReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AccountingPeriod() {
    }

    public AccountingPeriod(UUID organizationId, UUID fiscalYearId, String name,
                            int periodNumber, LocalDate startDate, LocalDate endDate) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.fiscalYearId = fiscalYearId;
        this.name = name;
        this.periodNumber = periodNumber;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = PeriodStatus.OPEN;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void close(UUID actorId) {
        this.status = PeriodStatus.CLOSED;
        this.closedAt = Instant.now();
        this.closedBy = actorId;
        this.updatedAt = Instant.now();
    }

    public void reopen(UUID actorId, String reason) {
        this.status = PeriodStatus.OPEN;
        this.reopenedAt = Instant.now();
        this.reopenedBy = actorId;
        this.reopenReason = reason;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getFiscalYearId() { return fiscalYearId; }
    public String getName() { return name; }
    public int getPeriodNumber() { return periodNumber; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public PeriodStatus getStatus() { return status; }
    public Instant getClosedAt() { return closedAt; }
    public UUID getClosedBy() { return closedBy; }
    public Instant getReopenedAt() { return reopenedAt; }
    public UUID getReopenedBy() { return reopenedBy; }
    public String getReopenReason() { return reopenReason; }
}
