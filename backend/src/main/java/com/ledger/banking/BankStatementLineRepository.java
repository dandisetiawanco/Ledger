package com.ledger.banking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankStatementLineRepository extends JpaRepository<BankStatementLine, UUID> {
    List<BankStatementLine> findByOrganizationIdAndBankAccountIdOrderByTransactionDateDescCreatedAtDesc(UUID organizationId, UUID bankAccountId);
    List<BankStatementLine> findByOrganizationIdAndBankAccountIdAndReconciledFalseOrderByTransactionDateDesc(UUID organizationId, UUID bankAccountId);
    Optional<BankStatementLine> findByIdAndOrganizationId(UUID id, UUID organizationId);
    boolean existsByOrganizationIdAndBankAccountIdAndFingerprint(UUID organizationId, UUID bankAccountId, String fingerprint);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM BankStatementLine l WHERE l.id = :id AND l.organizationId = :orgId")
    Optional<BankStatementLine> findByIdAndOrganizationIdForUpdate(@Param("id") UUID id, @Param("orgId") UUID organizationId);
}
