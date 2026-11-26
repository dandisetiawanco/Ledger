package com.ledger.contact;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, UUID> {
    List<CatalogItem> findByOrganizationIdOrderByNameAsc(UUID organizationId);
    Optional<CatalogItem> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
