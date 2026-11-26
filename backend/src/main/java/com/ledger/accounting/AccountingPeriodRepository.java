package com.ledger.accounting;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, UUID> {

    List<AccountingPeriod> findByOrganizationIdOrderByStartDateAsc(UUID organizationId);

    List<AccountingPeriod> findByFiscalYearIdOrderByPeriodNumberAsc(UUID fiscalYearId);

    Optional<AccountingPeriod> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM AccountingPeriod p WHERE p.id = :id AND p.organizationId = :orgId")
    Optional<AccountingPeriod> findByIdAndOrganizationIdForUpdate(@Param("id") UUID id, @Param("orgId") UUID orgId);

    @Query("""
            SELECT p FROM AccountingPeriod p
            WHERE p.organizationId = :orgId
              AND :date BETWEEN p.startDate AND p.endDate
            """)
    Optional<AccountingPeriod> findContainingDate(@Param("orgId") UUID organizationId, @Param("date") LocalDate date);
}
