package com.ledger.expense;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findByOrganizationIdOrderByExpenseDateDescCreatedAtDesc(UUID organizationId);

    List<Expense> findByOrganizationIdAndSupplierIdOrderByExpenseDateDesc(UUID organizationId, UUID supplierId);

    Optional<Expense> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Expense e WHERE e.id = :id AND e.organizationId = :orgId")
    Optional<Expense> findByIdAndOrganizationIdForUpdate(@Param("id") UUID id, @Param("orgId") UUID organizationId);

    @Query("""
            SELECT e FROM Expense e
            WHERE e.organizationId = :orgId
              AND e.expenseType = com.ledger.expense.ExpenseType.SUPPLIER_BILL
              AND e.status = com.ledger.expense.ExpenseStatus.POSTED
              AND e.expenseDate <= :asOfDate
            ORDER BY e.dueDate ASC
            """)
    List<Expense> findPostedSupplierBillsUpTo(@Param("orgId") UUID organizationId, @Param("asOfDate") LocalDate asOfDate);

    @Query("""
            SELECT e FROM Expense e
            WHERE e.organizationId = :orgId
              AND e.expenseType = com.ledger.expense.ExpenseType.SUPPLIER_BILL
              AND e.status = com.ledger.expense.ExpenseStatus.POSTED
              AND e.outstandingBalance > 0
            ORDER BY e.dueDate ASC
            """)
    List<Expense> findOpenPayables(@Param("orgId") UUID organizationId);
}
