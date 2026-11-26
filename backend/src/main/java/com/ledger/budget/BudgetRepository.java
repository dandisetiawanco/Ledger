package com.ledger.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    List<Budget> findByOrganizationIdOrderByStartDateDesc(UUID organizationId);
    Optional<Budget> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
