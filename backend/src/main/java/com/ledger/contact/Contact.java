package com.ledger.contact;

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
@Table(name = "contacts")
public class Contact {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, length = 16)
    private ContactType contactType;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "contact_person", length = 200)
    private String contactPerson;

    @Column(length = 255)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;

    @Column(name = "tax_id", length = 100)
    private String taxId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Contact() {
    }

    public Contact(UUID organizationId, ContactType contactType, String name, String contactPerson,
                   String email, String phone, String billingAddress, String taxId,
                   String currencyCode, int paymentTermsDays, String notes) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.contactType = contactType;
        this.name = name.trim();
        this.contactPerson = contactPerson;
        this.email = email;
        this.phone = phone;
        this.billingAddress = billingAddress;
        this.taxId = taxId;
        this.currencyCode = currencyCode;
        this.paymentTermsDays = paymentTermsDays;
        this.notes = notes;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isCustomer() { return contactType == ContactType.CUSTOMER || contactType == ContactType.BOTH; }
    public boolean isSupplier() { return contactType == ContactType.SUPPLIER || contactType == ContactType.BOTH; }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public ContactType getContactType() { return contactType; }
    public void setContactType(ContactType contactType) { this.contactType = contactType; touch(); }
    public String getName() { return name; }
    public void setName(String name) { this.name = name.trim(); touch(); }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; touch(); }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; touch(); }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; touch(); }
    public String getBillingAddress() { return billingAddress; }
    public void setBillingAddress(String billingAddress) { this.billingAddress = billingAddress; touch(); }
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; touch(); }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; touch(); }
    public int getPaymentTermsDays() { return paymentTermsDays; }
    public void setPaymentTermsDays(int paymentTermsDays) { this.paymentTermsDays = paymentTermsDays; touch(); }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; touch(); }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; touch(); }
    public Instant getCreatedAt() { return createdAt; }

    private void touch() { this.updatedAt = Instant.now(); }
}
