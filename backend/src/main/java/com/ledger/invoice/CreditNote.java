package com.ledger.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "credit_notes")
public class CreditNote {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "credit_note_number", nullable = false, length = 64)
    private String creditNoteNumber;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "credit_date", nullable = false)
    private LocalDate creditDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "base_total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseTotalAmount;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CreditNote() {
    }

    public CreditNote(UUID organizationId, String creditNoteNumber, UUID invoiceId, UUID customerId,
                      LocalDate creditDate, String reason, String currencyCode, BigDecimal exchangeRate,
                      BigDecimal netAmount, BigDecimal taxAmount, BigDecimal totalAmount,
                      BigDecimal baseTotalAmount, UUID journalEntryId, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.creditNoteNumber = creditNoteNumber;
        this.invoiceId = invoiceId;
        this.customerId = customerId;
        this.creditDate = creditDate;
        this.reason = reason;
        this.currencyCode = currencyCode;
        this.exchangeRate = exchangeRate;
        this.netAmount = netAmount;
        this.taxAmount = taxAmount;
        this.totalAmount = totalAmount;
        this.baseTotalAmount = baseTotalAmount;
        this.status = "POSTED";
        this.journalEntryId = journalEntryId;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getCreditNoteNumber() { return creditNoteNumber; }
    public UUID getInvoiceId() { return invoiceId; }
    public UUID getCustomerId() { return customerId; }
    public LocalDate getCreditDate() { return creditDate; }
    public String getReason() { return reason; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getBaseTotalAmount() { return baseTotalAmount; }
    public String getStatus() { return status; }
    public UUID getJournalEntryId() { return journalEntryId; }
    public Instant getCreatedAt() { return createdAt; }
}
