package com.ledger.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findByOrganizationIdAndEntityTypeAndEntityIdOrderByUploadedAtDesc(
            UUID organizationId, String entityType, UUID entityId
    );
    Optional<Attachment> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
