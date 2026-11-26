package com.ledger.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_items")
public class CatalogItem {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "revenue_account_id", nullable = false)
    private UUID revenueAccountId;

    @Column(name = "default_tax_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal defaultTaxRate;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected CatalogItem() {
    }

    public CatalogItem(UUID organizationId, String sku, String name, String description,
                       BigDecimal unitPrice, String currencyCode, UUID revenueAccountId, BigDecimal defaultTaxRate) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.sku = sku;
        this.name = name.trim();
        this.description = description;
        this.unitPrice = unitPrice;
        this.currencyCode = currencyCode;
        this.revenueAccountId = revenueAccountId;
        this.defaultTaxRate = defaultTaxRate != null ? defaultTaxRate : BigDecimal.ZERO;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; touch(); }
    public String getName() { return name; }
    public void setName(String name) { this.name = name.trim(); touch(); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; touch(); }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; touch(); }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; touch(); }
    public UUID getRevenueAccountId() { return revenueAccountId; }
    public void setRevenueAccountId(UUID revenueAccountId) { this.revenueAccountId = revenueAccountId; touch(); }
    public BigDecimal getDefaultTaxRate() { return defaultTaxRate; }
    public void setDefaultTaxRate(BigDecimal defaultTaxRate) { this.defaultTaxRate = defaultTaxRate; touch(); }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
