package com.ledger.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_requests")
public class ApprovalRequest {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "entity_reference", nullable = false, length = 120)
    private String entityReference;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "approver_id")
    private UUID approverId;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    private long version;

    protected ApprovalRequest() {
    }

    public ApprovalRequest(UUID organizationId, String entityType, UUID entityId, String entityReference,
                           BigDecimal amount, String currencyCode, UUID requesterId) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.entityReference = entityReference;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.status = "PENDING";
        this.requesterId = requesterId;
        this.requestedAt = Instant.now();
    }

    public void decide(String newStatus, UUID approverId, String reason) {
        this.status = newStatus;
        this.approverId = approverId;
        this.decisionReason = reason;
        this.decidedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getEntityReference() { return entityReference; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public String getStatus() { return status; }
    public UUID getRequesterId() { return requesterId; }
    public UUID getApproverId() { return approverId; }
    public String getDecisionReason() { return decisionReason; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
