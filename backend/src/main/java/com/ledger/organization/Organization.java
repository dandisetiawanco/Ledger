package com.ledger.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "business_type", length = 100)
    private String businessType;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(nullable = false, length = 20)
    private String locale;

    @Column(name = "fiscal_year_start_month", nullable = false)
    private int fiscalYearStartMonth;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "tax_id", length = 100)
    private String taxId;

    @Column(name = "logo_attachment_id")
    private UUID logoAttachmentId;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Organization() {
    }

    public Organization(String name, String slug, String businessType, String baseCurrency,
                        String timeZone, String locale, int fiscalYearStartMonth, boolean demo) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.slug = slug;
        this.businessType = businessType;
        this.baseCurrency = baseCurrency;
        this.timeZone = timeZone;
        this.locale = locale;
        this.fiscalYearStartMonth = fiscalYearStartMonth;
        this.demo = demo;
        this.onboardingCompleted = false;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; this.updatedAt = Instant.now(); }
    public String getSlug() { return slug; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; this.updatedAt = Instant.now(); }
    public String getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; this.updatedAt = Instant.now(); }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; this.updatedAt = Instant.now(); }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; this.updatedAt = Instant.now(); }
    public int getFiscalYearStartMonth() { return fiscalYearStartMonth; }
    public void setFiscalYearStartMonth(int fiscalYearStartMonth) { this.fiscalYearStartMonth = fiscalYearStartMonth; this.updatedAt = Instant.now(); }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; this.updatedAt = Instant.now(); }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; this.updatedAt = Instant.now(); }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; this.updatedAt = Instant.now(); }
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; this.updatedAt = Instant.now(); }
    public UUID getLogoAttachmentId() { return logoAttachmentId; }
    public void setLogoAttachmentId(UUID logoAttachmentId) { this.logoAttachmentId = logoAttachmentId; this.updatedAt = Instant.now(); }
    public boolean isDemo() { return demo; }
    public boolean isOnboardingCompleted() { return onboardingCompleted; }
    public void setOnboardingCompleted(boolean onboardingCompleted) { this.onboardingCompleted = onboardingCompleted; this.updatedAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
