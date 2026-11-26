package com.ledger.organization;

import java.util.UUID;

/**
 * Extension point invoked synchronously inside the organization creation transaction
 * to seed default chart of accounts, fiscal periods, and expense categories.
 */
public interface OrganizationInitializer {
    void initializeOrganization(Organization organization, UUID actorUserId);
}
