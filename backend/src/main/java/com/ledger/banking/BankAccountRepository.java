package com.ledger.banking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {
    List<BankAccount> findByOrganizationIdOrderByNameAsc(UUID organizationId);
    Optional<BankAccount> findByIdAndOrganizationId(UUID id, UUID organizationId);
    boolean existsByOrganizationIdAndGlAccountId(UUID organizationId, UUID glAccountId);
}
