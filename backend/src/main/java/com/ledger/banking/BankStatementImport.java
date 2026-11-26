package com.ledger.banking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bank_statement_imports")
public class BankStatementImport {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "bank_account_id", nullable = false)
    private UUID bankAccountId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_hash", nullable = false, length = 128)
    private String fileHash;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;

    @Column(name = "imported_by")
    private UUID importedBy;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    protected BankStatementImport() {
    }

    public BankStatementImport(UUID organizationId, UUID bankAccountId, String fileName, String fileHash,
                               int totalRows, int importedRows, int duplicateRows, UUID importedBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.bankAccountId = bankAccountId;
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.totalRows = totalRows;
        this.importedRows = importedRows;
        this.duplicateRows = duplicateRows;
        this.importedBy = importedBy;
        this.importedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getBankAccountId() { return bankAccountId; }
    public String getFileName() { return fileName; }
    public int getTotalRows() { return totalRows; }
    public int getImportedRows() { return importedRows; }
    public int getDuplicateRows() { return duplicateRows; }
    public Instant getImportedAt() { return importedAt; }
}
