package com.ledger.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payment_allocations")
public class PaymentAllocation {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "expense_id")
    private UUID expenseId;

    @Column(name = "allocation_date", nullable = false)
    private LocalDate allocationDate;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount;

    @Column(name = "base_allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseAllocatedAmount;

    @Column(name = "realized_fx_gain_loss", nullable = false, precision = 19, scale = 4)
    private BigDecimal realizedFxGainLoss;

    @Column(nullable = false)
    private boolean reversed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentAllocation() {
    }

    public PaymentAllocation(UUID organizationId, UUID invoiceId, UUID expenseId,
                             LocalDate allocationDate, BigDecimal allocatedAmount,
                             BigDecimal baseAllocatedAmount, BigDecimal realizedFxGainLoss) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.invoiceId = invoiceId;
        this.expenseId = expenseId;
        this.allocationDate = allocationDate;
        this.allocatedAmount = allocatedAmount;
        this.baseAllocatedAmount = baseAllocatedAmount;
        this.realizedFxGainLoss = realizedFxGainLoss;
        this.reversed = false;
        this.createdAt = Instant.now();
    }

    void attachTo(Payment payment) {
        this.payment = payment;
    }

    public void markReversed() {
        this.reversed = true;
    }

    public UUID getId() { return id; }
    public Payment getPayment() { return payment; }
    public UUID getInvoiceId() { return invoiceId; }
    public UUID getExpenseId() { return expenseId; }
    public LocalDate getAllocationDate() { return allocationDate; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public BigDecimal getBaseAllocatedAmount() { return baseAllocatedAmount; }
    public BigDecimal getRealizedFxGainLoss() { return realizedFxGainLoss; }
    public boolean isReversed() { return reversed; }
}
