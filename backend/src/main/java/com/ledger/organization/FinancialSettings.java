package com.ledger.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "financial_settings")
public class FinancialSettings {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, unique = true)
    private UUID organizationId;

    @Column(name = "invoice_prefix", nullable = false, length = 20)
    private String invoicePrefix = "INV";

    @Column(name = "credit_note_prefix", nullable = false, length = 20)
    private String creditNotePrefix = "CN";

    @Column(name = "journal_prefix", nullable = false, length = 20)
    private String journalPrefix = "JE";

    @Column(name = "payment_prefix", nullable = false, length = 20)
    private String paymentPrefix = "PAY";

    @Column(name = "expense_prefix", nullable = false, length = 20)
    private String expensePrefix = "EXP";

    @Column(name = "default_payment_terms_days", nullable = false)
    private int defaultPaymentTermsDays = 30;

    @Column(name = "default_tax_name", nullable = false, length = 60)
    private String defaultTaxName = "VAT";

    @Column(name = "default_tax_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal defaultTaxRate = BigDecimal.ZERO;

    @Column(name = "tax_inclusive_default", nullable = false)
    private boolean taxInclusiveDefault = false;

    @Column(name = "require_expense_approval", nullable = false)
    private boolean requireExpenseApproval = true;

    @Column(name = "require_journal_approval", nullable = false)
    private boolean requireJournalApproval = false;

    @Column(name = "require_invoice_approval", nullable = false)
    private boolean requireInvoiceApproval = false;

    @Column(name = "enforce_separation_of_duties", nullable = false)
    private boolean enforceSeparationOfDuties = true;

    @Column(name = "expense_approval_threshold", nullable = false, precision = 19, scale = 4)
    private BigDecimal expenseApprovalThreshold = new BigDecimal("1000000.0000");

    @Column(name = "payment_instructions", columnDefinition = "TEXT")
    private String paymentInstructions;

    @Column(name = "invoice_footer_notes", columnDefinition = "TEXT")
    private String invoiceFooterNotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected FinancialSettings() {
    }

    public FinancialSettings(UUID organizationId) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getInvoicePrefix() { return invoicePrefix; }
    public void setInvoicePrefix(String invoicePrefix) { this.invoicePrefix = invoicePrefix; touch(); }
    public String getCreditNotePrefix() { return creditNotePrefix; }
    public void setCreditNotePrefix(String creditNotePrefix) { this.creditNotePrefix = creditNotePrefix; touch(); }
    public String getJournalPrefix() { return journalPrefix; }
    public void setJournalPrefix(String journalPrefix) { this.journalPrefix = journalPrefix; touch(); }
    public String getPaymentPrefix() { return paymentPrefix; }
    public void setPaymentPrefix(String paymentPrefix) { this.paymentPrefix = paymentPrefix; touch(); }
    public String getExpensePrefix() { return expensePrefix; }
    public void setExpensePrefix(String expensePrefix) { this.expensePrefix = expensePrefix; touch(); }
    public int getDefaultPaymentTermsDays() { return defaultPaymentTermsDays; }
    public void setDefaultPaymentTermsDays(int defaultPaymentTermsDays) { this.defaultPaymentTermsDays = defaultPaymentTermsDays; touch(); }
    public String getDefaultTaxName() { return defaultTaxName; }
    public void setDefaultTaxName(String defaultTaxName) { this.defaultTaxName = defaultTaxName; touch(); }
    public BigDecimal getDefaultTaxRate() { return defaultTaxRate; }
    public void setDefaultTaxRate(BigDecimal defaultTaxRate) { this.defaultTaxRate = defaultTaxRate; touch(); }
    public boolean isTaxInclusiveDefault() { return taxInclusiveDefault; }
    public void setTaxInclusiveDefault(boolean taxInclusiveDefault) { this.taxInclusiveDefault = taxInclusiveDefault; touch(); }
    public boolean isRequireExpenseApproval() { return requireExpenseApproval; }
    public void setRequireExpenseApproval(boolean requireExpenseApproval) { this.requireExpenseApproval = requireExpenseApproval; touch(); }
    public boolean isRequireJournalApproval() { return requireJournalApproval; }
    public void setRequireJournalApproval(boolean requireJournalApproval) { this.requireJournalApproval = requireJournalApproval; touch(); }
    public boolean isRequireInvoiceApproval() { return requireInvoiceApproval; }
    public void setRequireInvoiceApproval(boolean requireInvoiceApproval) { this.requireInvoiceApproval = requireInvoiceApproval; touch(); }
    public boolean isEnforceSeparationOfDuties() { return enforceSeparationOfDuties; }
    public void setEnforceSeparationOfDuties(boolean enforceSeparationOfDuties) { this.enforceSeparationOfDuties = enforceSeparationOfDuties; touch(); }
    public BigDecimal getExpenseApprovalThreshold() { return expenseApprovalThreshold; }
    public void setExpenseApprovalThreshold(BigDecimal expenseApprovalThreshold) { this.expenseApprovalThreshold = expenseApprovalThreshold; touch(); }
    public String getPaymentInstructions() { return paymentInstructions; }
    public void setPaymentInstructions(String paymentInstructions) { this.paymentInstructions = paymentInstructions; touch(); }
    public String getInvoiceFooterNotes() { return invoiceFooterNotes; }
    public void setInvoiceFooterNotes(String invoiceFooterNotes) { this.invoiceFooterNotes = invoiceFooterNotes; touch(); }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
