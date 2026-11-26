package com.ledger.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByOrganizationIdOrderByCodeAsc(UUID organizationId);
    List<Account> findByOrganizationIdAndArchivedFalseOrderByCodeAsc(UUID organizationId);
    Optional<Account> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<Account> findByOrganizationIdAndCode(UUID organizationId, String code);
    Optional<Account> findByOrganizationIdAndSystemCode(UUID organizationId, String systemCode);
    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
    boolean existsByParentAccountId(UUID parentAccountId);
}
