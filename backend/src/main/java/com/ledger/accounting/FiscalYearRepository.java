package com.ledger.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FiscalYearRepository extends JpaRepository<FiscalYear, UUID> {
    List<FiscalYear> findByOrganizationIdOrderByStartDateDesc(UUID organizationId);
    Optional<FiscalYear> findByIdAndOrganizationId(UUID id, UUID organizationId);
    boolean existsByOrganizationIdAndName(UUID organizationId, String name);
}
