package com.ledger.invoice;

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
@Table(name = "invoice_lines")
public class InvoiceLine {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "catalog_item_id")
    private UUID catalogItemId;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "revenue_account_id", nullable = false)
    private UUID revenueAccountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "discount_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal discountRate;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "tax_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal;

    protected InvoiceLine() {
    }

    public InvoiceLine(UUID organizationId, int lineNumber, UUID catalogItemId, String description,
                       UUID revenueAccountId, BigDecimal quantity, BigDecimal unitPrice,
                       BigDecimal discountRate, BigDecimal discountAmount, BigDecimal taxRate,
                       BigDecimal taxAmount, BigDecimal netAmount, BigDecimal lineTotal) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.lineNumber = lineNumber;
        this.catalogItemId = catalogItemId;
        this.description = description;
        this.revenueAccountId = revenueAccountId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.discountRate = discountRate;
        this.discountAmount = discountAmount;
        this.taxRate = taxRate;
        this.taxAmount = taxAmount;
        this.netAmount = netAmount;
        this.lineTotal = lineTotal;
    }

    void attachTo(Invoice invoice) {
        this.invoice = invoice;
    }

    public UUID getId() { return id; }
    public int getLineNumber() { return lineNumber; }
    public UUID getCatalogItemId() { return catalogItemId; }
    public String getDescription() { return description; }
    public UUID getRevenueAccountId() { return revenueAccountId; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getDiscountRate() { return discountRate; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
