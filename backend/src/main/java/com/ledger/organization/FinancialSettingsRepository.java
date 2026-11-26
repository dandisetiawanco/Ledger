package com.ledger.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FinancialSettingsRepository extends JpaRepository<FinancialSettings, UUID> {
    Optional<FinancialSettings> findByOrganizationId(UUID organizationId);
}
