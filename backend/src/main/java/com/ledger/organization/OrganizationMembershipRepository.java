package com.ledger.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {
    Optional<OrganizationMembership> findByOrganizationIdAndUserIdAndActiveTrue(UUID organizationId, UUID userId);
    Optional<OrganizationMembership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
    List<OrganizationMembership> findByUserIdAndActiveTrue(UUID userId);
    List<OrganizationMembership> findByOrganizationId(UUID organizationId);
    long countByOrganizationIdAndRoleAndActiveTrue(UUID organizationId, OrgRole role);
}
