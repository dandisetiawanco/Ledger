package com.ledger.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, UUID> {
    List<ReconciliationRecord> findByOrganizationIdAndBankAccountIdOrderByReconciledAtDesc(UUID organizationId, UUID bankAccountId);
    List<ReconciliationRecord> findByOrganizationIdAndBankAccountIdAndStatus(UUID organizationId, UUID bankAccountId, String status);
    Optional<ReconciliationRecord> findByIdAndOrganizationId(UUID id, UUID organizationId);
    boolean existsByActiveBankLineId(UUID bankStatementLineId);
    boolean existsByActiveJournalLineId(UUID journalLineId);
}
