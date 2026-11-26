package com.ledger.journal;

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
@Table(name = "journal_lines")
public class JournalLine {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(length = 500)
    private String description;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "original_debit", nullable = false, precision = 19, scale = 4)
    private BigDecimal originalDebit;

    @Column(name = "original_credit", nullable = false, precision = 19, scale = 4)
    private BigDecimal originalCredit;

    @Column(name = "debit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal debitAmount;

    @Column(name = "credit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditAmount;

    protected JournalLine() {
    }

    public JournalLine(UUID organizationId, int lineNumber, UUID accountId, UUID contactId,
                       String description, String currencyCode,
                       BigDecimal originalDebit, BigDecimal originalCredit,
                       BigDecimal debitAmount, BigDecimal creditAmount) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.lineNumber = lineNumber;
        this.accountId = accountId;
        this.contactId = contactId;
        this.description = description;
        this.currencyCode = currencyCode;
        this.originalDebit = originalDebit;
        this.originalCredit = originalCredit;
        this.debitAmount = debitAmount;
        this.creditAmount = creditAmount;
    }

    void attachTo(JournalEntry entry) {
        this.journalEntry = entry;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public JournalEntry getJournalEntry() { return journalEntry; }
    public int getLineNumber() { return lineNumber; }
    public UUID getAccountId() { return accountId; }
    public UUID getContactId() { return contactId; }
    public String getDescription() { return description; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getOriginalDebit() { return originalDebit; }
    public BigDecimal getOriginalCredit() { return originalCredit; }
    public BigDecimal getDebitAmount() { return debitAmount; }
    public BigDecimal getCreditAmount() { return creditAmount; }
}
