package com.ledger.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, UUID> {
    List<ExpenseCategory> findByOrganizationIdOrderByNameAsc(UUID organizationId);
    Optional<ExpenseCategory> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
