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
@Table(name = "bank_statement_lines")
public class BankStatementLine {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "bank_account_id", nullable = false)
    private UUID bankAccountId;

    @Column(name = "statement_import_id", nullable = false)
    private UUID statementImportId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 160)
    private String reference;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 128)
    private String fingerprint;

    @Column(nullable = false)
    private boolean reconciled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected BankStatementLine() {
    }

    public BankStatementLine(UUID organizationId, UUID bankAccountId, UUID statementImportId,
                             LocalDate transactionDate, String description, String reference,
                             BigDecimal amount, String fingerprint) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.bankAccountId = bankAccountId;
        this.statementImportId = statementImportId;
        this.transactionDate = transactionDate;
        this.description = description;
        this.reference = reference;
        this.amount = amount;
        this.fingerprint = fingerprint;
        this.reconciled = false;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getBankAccountId() { return bankAccountId; }
    public UUID getStatementImportId() { return statementImportId; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public String getDescription() { return description; }
    public String getReference() { return reference; }
    public BigDecimal getAmount() { return amount; }
    public String getFingerprint() { return fingerprint; }
    public boolean isReconciled() { return reconciled; }
    public void setReconciled(boolean reconciled) { this.reconciled = reconciled; }
    public Instant getCreatedAt() { return createdAt; }
}
