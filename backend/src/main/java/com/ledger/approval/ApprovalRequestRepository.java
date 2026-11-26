package com.ledger.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {
    List<ApprovalRequest> findByOrganizationIdOrderByRequestedAtDesc(UUID organizationId);
    List<ApprovalRequest> findByOrganizationIdAndStatusOrderByRequestedAtDesc(UUID organizationId, String status);
    Optional<ApprovalRequest> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<ApprovalRequest> findFirstByOrganizationIdAndEntityTypeAndEntityIdAndStatus(
            UUID organizationId, String entityType, UUID entityId, String status
    );
}
