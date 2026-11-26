package com.ledger.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByOrganizationIdOrderByPaymentDateDescCreatedAtDesc(UUID organizationId);
    List<Payment> findByOrganizationIdAndContactIdOrderByPaymentDateDesc(UUID organizationId, UUID contactId);
    Optional<Payment> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<Payment> findByOrganizationIdAndIdempotencyKey(UUID organizationId, String idempotencyKey);
}
