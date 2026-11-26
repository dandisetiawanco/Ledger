package com.ledger.contact;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {
    List<Contact> findByOrganizationIdOrderByNameAsc(UUID organizationId);
    List<Contact> findByOrganizationIdAndContactTypeInOrderByNameAsc(UUID organizationId, List<ContactType> types);
    Optional<Contact> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
