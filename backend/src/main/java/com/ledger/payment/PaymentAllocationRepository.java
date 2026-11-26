package com.ledger.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {

    @Query("""
            SELECT pa FROM PaymentAllocation pa
            WHERE pa.organizationId = :orgId
              AND pa.invoiceId IS NOT NULL
              AND pa.reversed = false
              AND pa.allocationDate <= :asOfDate
            """)
    List<PaymentAllocation> findActiveInvoiceAllocationsUpTo(@Param("orgId") UUID organizationId, @Param("asOfDate") LocalDate asOfDate);

    @Query("""
            SELECT pa FROM PaymentAllocation pa
            WHERE pa.organizationId = :orgId
              AND pa.expenseId IS NOT NULL
              AND pa.reversed = false
              AND pa.allocationDate <= :asOfDate
            """)
    List<PaymentAllocation> findActiveExpenseAllocationsUpTo(@Param("orgId") UUID organizationId, @Param("asOfDate") LocalDate asOfDate);

    List<PaymentAllocation> findByOrganizationIdAndInvoiceIdAndReversedFalse(UUID organizationId, UUID invoiceId);

    List<PaymentAllocation> findByOrganizationIdAndExpenseIdAndReversedFalse(UUID organizationId, UUID expenseId);
}
