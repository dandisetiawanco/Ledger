package com.ledger.journal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndBusinessEventKey(UUID organizationId, String businessEventKey);

    Optional<JournalEntry> findByOrganizationIdAndBusinessEventKey(UUID organizationId, String businessEventKey);

    long countByOrganizationIdAndAccountingPeriodIdAndStatusIn(
            UUID organizationId, UUID accountingPeriodId, List<JournalStatus> statuses
    );

    @Query("""
            SELECT j FROM JournalEntry j
            WHERE j.organizationId = :orgId
              AND (:status IS NULL OR j.status = :status)
              AND (:sourceType IS NULL OR j.sourceType = :sourceType)
              AND (:fromDate IS NULL OR j.entryDate >= :fromDate)
              AND (:toDate IS NULL OR j.entryDate <= :toDate)
              AND (:search IS NULL OR LOWER(j.description) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(j.entryNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(j.reference, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY j.entryDate DESC, j.createdAt DESC
            """)
    Page<JournalEntry> search(
            @Param("orgId") UUID organizationId,
            @Param("status") JournalStatus status,
            @Param("sourceType") JournalSourceType sourceType,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            SELECT j FROM JournalEntry j
            WHERE j.organizationId = :orgId
              AND j.status IN (com.ledger.journal.JournalStatus.POSTED, com.ledger.journal.JournalStatus.REVERSED)
              AND j.entryDate BETWEEN :fromDate AND :toDate
            ORDER BY j.entryDate ASC, j.createdAt ASC
            """)
    List<JournalEntry> findPostedInRange(
            @Param("orgId") UUID organizationId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
}
