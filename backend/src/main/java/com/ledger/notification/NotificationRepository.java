package com.ledger.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findTop50ByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    Optional<Notification> findByIdAndOrganizationId(UUID id, UUID organizationId);
    boolean existsByOrganizationIdAndDedupKey(UUID organizationId, String dedupKey);
}
