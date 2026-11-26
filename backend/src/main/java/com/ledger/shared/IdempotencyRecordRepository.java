package com.ledger.shared;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByOrganizationIdAndOperationAndIdempotencyKey(
            UUID organizationId, String operation, String idempotencyKey
    );
}
