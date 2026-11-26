package com.ledger.banking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BankStatementImportRepository extends JpaRepository<BankStatementImport, UUID> {
    List<BankStatementImport> findByOrganizationIdAndBankAccountIdOrderByImportedAtDesc(UUID organizationId, UUID bankAccountId);
}
