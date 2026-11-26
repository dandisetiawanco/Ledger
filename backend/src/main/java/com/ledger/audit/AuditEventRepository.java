package com.ledger.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    @Query("""
            SELECT a FROM AuditEvent a
            WHERE a.organizationId = :orgId
              AND (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:search IS NULL OR LOWER(a.summary) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(a.actorEmail, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY a.occurredAt DESC
            """)
    Page<AuditEvent> searchByOrganization(
            @Param("orgId") UUID organizationId,
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("search") String search,
            Pageable pageable
    );
}
