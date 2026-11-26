package com.ledger.invoice;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findByOrganizationIdOrderByIssueDateDescCreatedAtDesc(UUID organizationId);

    List<Invoice> findByOrganizationIdAndCustomerIdOrderByIssueDateDesc(UUID organizationId, UUID customerId);

    Optional<Invoice> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id AND i.organizationId = :orgId")
    Optional<Invoice> findByIdAndOrganizationIdForUpdate(@Param("id") UUID id, @Param("orgId") UUID organizationId);

    @Query("""
            SELECT i FROM Invoice i
            WHERE i.organizationId = :orgId
              AND i.status IN (com.ledger.invoice.InvoiceStatus.ISSUED, com.ledger.invoice.InvoiceStatus.PARTIALLY_PAID, com.ledger.invoice.InvoiceStatus.PAID)
              AND i.issueDate <= :asOfDate
            ORDER BY i.dueDate ASC
            """)
    List<Invoice> findPostedUpTo(@Param("orgId") UUID organizationId, @Param("asOfDate") LocalDate asOfDate);

    @Query("""
            SELECT i FROM Invoice i
            WHERE i.organizationId = :orgId
              AND i.status IN (com.ledger.invoice.InvoiceStatus.ISSUED, com.ledger.invoice.InvoiceStatus.PARTIALLY_PAID)
              AND i.outstandingBalance > 0
            ORDER BY i.dueDate ASC
            """)
    List<Invoice> findOpenReceivables(@Param("orgId") UUID organizationId);
}
