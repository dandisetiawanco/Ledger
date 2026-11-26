package com.ledger.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {
    List<CreditNote> findByOrganizationIdAndInvoiceIdOrderByCreditDateAsc(UUID organizationId, UUID invoiceId);
    Optional<CreditNote> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("""
            SELECT c FROM CreditNote c
            WHERE c.organizationId = :orgId
              AND c.creditDate <= :asOfDate
            """)
    List<CreditNote> findByOrganizationIdUpTo(@Param("orgId") UUID organizationId, @Param("asOfDate") LocalDate asOfDate);
}
