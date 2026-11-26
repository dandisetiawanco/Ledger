package com.ledger.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "budget_lines")
public class BudgetLine {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "period_month")
    private Integer periodMonth;

    @Column(name = "planned_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal plannedAmount;

    @Column(length = 500)
    private String notes;

    protected BudgetLine() {
    }

    public BudgetLine(UUID organizationId, UUID accountId, Integer periodMonth, BigDecimal plannedAmount, String notes) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.accountId = accountId;
        this.periodMonth = periodMonth;
        this.plannedAmount = plannedAmount;
        this.notes = notes;
    }

    void attachTo(Budget budget) {
        this.budget = budget;
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public Integer getPeriodMonth() { return periodMonth; }
    public BigDecimal getPlannedAmount() { return plannedAmount; }
    public String getNotes() { return notes; }
}
