package com.ledger.journal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalLineRepository extends JpaRepository<JournalLine, UUID> {

    boolean existsByAccountId(UUID accountId);

    Optional<JournalLine> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Aggregates posted debit and credit totals per account up to an inclusive date.
     * Both POSTED and REVERSED entries are included because reversing entries explicitly offset REVERSED entries.
     */
    @Query("""
            SELECT jl.accountId, COALESCE(SUM(jl.debitAmount), 0), COALESCE(SUM(jl.creditAmount), 0)
            FROM JournalLine jl
            JOIN jl.journalEntry je
            WHERE jl.organizationId = :orgId
              AND je.status IN (com.ledger.journal.JournalStatus.POSTED, com.ledger.journal.JournalStatus.REVERSED)
              AND je.entryDate <= :asOfDate
            GROUP BY jl.accountId
            """)
    List<Object[]> sumPostedByAccountUpTo(@Param("orgId") UUID organizationId, @Param("asOfDate") LocalDate asOfDate);

    @Query("""
            SELECT jl.accountId, COALESCE(SUM(jl.debitAmount), 0), COALESCE(SUM(jl.creditAmount), 0)
            FROM JournalLine jl
            JOIN jl.journalEntry je
            WHERE jl.organizationId = :orgId
              AND je.status IN (com.ledger.journal.JournalStatus.POSTED, com.ledger.journal.JournalStatus.REVERSED)
              AND je.entryDate >= :fromDate
              AND je.entryDate <= :toDate
            GROUP BY jl.accountId
            """)
    List<Object[]> sumPostedByAccountInRange(
            @Param("orgId") UUID organizationId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            SELECT COALESCE(SUM(jl.debitAmount), 0), COALESCE(SUM(jl.creditAmount), 0)
            FROM JournalLine jl
            JOIN jl.journalEntry je
            WHERE jl.organizationId = :orgId
              AND jl.accountId = :accountId
              AND je.status IN (com.ledger.journal.JournalStatus.POSTED, com.ledger.journal.JournalStatus.REVERSED)
              AND je.entryDate < :beforeDate
            """)
    List<Object[]> sumPostedForAccountBefore(
            @Param("orgId") UUID organizationId,
            @Param("accountId") UUID accountId,
            @Param("beforeDate") LocalDate beforeDate
    );

    @Query("""
            SELECT jl
            FROM JournalLine jl
            JOIN FETCH jl.journalEntry je
            WHERE jl.organizationId = :orgId
              AND jl.accountId = :accountId
              AND je.status IN (com.ledger.journal.JournalStatus.POSTED, com.ledger.journal.JournalStatus.REVERSED)
              AND (:fromDate IS NULL OR je.entryDate >= :fromDate)
              AND (:toDate IS NULL OR je.entryDate <= :toDate)
              AND (:search IS NULL OR LOWER(je.description) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(je.entryNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(je.reference, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(jl.description, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY je.entryDate ASC, je.createdAt ASC, jl.lineNumber ASC
            """)
    List<JournalLine> findPostedLinesForAccount(
            @Param("orgId") UUID organizationId,
            @Param("accountId") UUID accountId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("search") String search
    );

    @Query("""
            SELECT jl
            FROM JournalLine jl
            JOIN FETCH jl.journalEntry je
            WHERE jl.organizationId = :orgId
              AND jl.accountId = :accountId
              AND je.status = com.ledger.journal.JournalStatus.POSTED
            ORDER BY je.entryDate DESC, je.createdAt DESC
            """)
    List<JournalLine> findActivePostedLinesForAccount(
            @Param("orgId") UUID organizationId,
            @Param("accountId") UUID accountId
    );
}
