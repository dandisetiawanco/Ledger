package com.ledger.banking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bank_accounts")
public class BankAccount {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "institution_name", nullable = false, length = 160)
    private String institutionName;

    @Column(name = "masked_account_number", length = 64)
    private String maskedAccountNumber;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "gl_account_id", nullable = false)
    private UUID glAccountId;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingBalance;

    @Column(name = "opening_balance_date")
    private LocalDate openingBalanceDate;

    @Column(name = "opening_journal_entry_id")
    private UUID openingJournalEntryId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected BankAccount() {
    }

    public BankAccount(UUID organizationId, String name, String institutionName, String maskedAccountNumber,
                       String currencyCode, UUID glAccountId, BigDecimal openingBalance,
                       LocalDate openingBalanceDate, UUID openingJournalEntryId) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.name = name.trim();
        this.institutionName = institutionName.trim();
        this.maskedAccountNumber = maskedAccountNumber;
        this.currencyCode = currencyCode;
        this.glAccountId = glAccountId;
        this.openingBalance = openingBalance != null ? openingBalance : BigDecimal.ZERO;
        this.openingBalanceDate = openingBalanceDate;
        this.openingJournalEntryId = openingJournalEntryId;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public String getInstitutionName() { return institutionName; }
    public String getMaskedAccountNumber() { return maskedAccountNumber; }
    public String getCurrencyCode() { return currencyCode; }
    public UUID getGlAccountId() { return glAccountId; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public LocalDate getOpeningBalanceDate() { return openingBalanceDate; }
    public UUID getOpeningJournalEntryId() { return openingJournalEntryId; }
    public boolean isActive() { return active; }
}
