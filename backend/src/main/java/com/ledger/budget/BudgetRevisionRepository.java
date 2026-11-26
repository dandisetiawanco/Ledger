package com.ledger.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BudgetRevisionRepository extends JpaRepository<BudgetRevision, UUID> {
    List<BudgetRevision> findByOrganizationIdAndBudgetIdOrderByRevisionNumberDesc(UUID organizationId, UUID budgetId);
}
