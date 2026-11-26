package com.ledger.expense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "expense_categories")
public class ExpenseCategory {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "default_account_id", nullable = false)
    private UUID defaultAccountId;

    @Column(nullable = false)
    private boolean active;

    protected ExpenseCategory() {
    }

    public ExpenseCategory(UUID organizationId, String name, UUID defaultAccountId) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.name = name.trim();
        this.defaultAccountId = defaultAccountId;
        this.active = true;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name.trim(); }
    public UUID getDefaultAccountId() { return defaultAccountId; }
    public void setDefaultAccountId(UUID defaultAccountId) { this.defaultAccountId = defaultAccountId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
