package com.ledger.accounting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 24)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false, length = 8)
    private NormalBalance normalBalance;

    @Column(name = "parent_account_id")
    private UUID parentAccountId;

    @Column(name = "system_code", length = 40)
    private String systemCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "cash_flow_category", length = 24)
    private CashFlowCategory cashFlowCategory;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "allow_posting", nullable = false)
    private boolean allowPosting;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Account() {
    }

    public Account(UUID organizationId, String code, String name, AccountType accountType,
                   NormalBalance normalBalance, UUID parentAccountId, String systemCode,
                   CashFlowCategory cashFlowCategory, String description, String currencyCode, boolean allowPosting) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.code = code.trim();
        this.name = name.trim();
        this.accountType = accountType;
        this.normalBalance = normalBalance != null ? normalBalance : accountType.defaultNormalBalance();
        this.parentAccountId = parentAccountId;
        this.systemCode = systemCode;
        this.cashFlowCategory = cashFlowCategory;
        this.description = description;
        this.currencyCode = currencyCode;
        this.allowPosting = allowPosting;
        this.active = true;
        this.archived = false;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code.trim(); touch(); }
    public String getName() { return name; }
    public void setName(String name) { this.name = name.trim(); touch(); }
    public AccountType getAccountType() { return accountType; }
    public NormalBalance getNormalBalance() { return normalBalance; }
    public UUID getParentAccountId() { return parentAccountId; }
    public void setParentAccountId(UUID parentAccountId) { this.parentAccountId = parentAccountId; touch(); }
    public String getSystemCode() { return systemCode; }
    public CashFlowCategory getCashFlowCategory() { return cashFlowCategory; }
    public void setCashFlowCategory(CashFlowCategory cashFlowCategory) { this.cashFlowCategory = cashFlowCategory; touch(); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; touch(); }
    public String getCurrencyCode() { return currencyCode; }
    public boolean isAllowPosting() { return allowPosting; }
    public void setAllowPosting(boolean allowPosting) { this.allowPosting = allowPosting; touch(); }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; touch(); }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; touch(); }
    public Instant getCreatedAt() { return createdAt; }

    private void touch() { this.updatedAt = Instant.now(); }
}
