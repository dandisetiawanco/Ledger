package com.ledger.shared;

import com.ledger.organization.OrgRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.UUID;

public record LedgerPrincipal(
        UUID userId,
        String email,
        String fullName,
        boolean demoUser,
        UUID sessionId,
        UUID organizationId,
        OrgRole role,
        boolean demoOrganization
) {
    public static LedgerPrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LedgerPrincipal principal)) {
            throw LedgerException.unauthorized("Authentication required");
        }
        return principal;
    }

    public UUID requireOrganizationId() {
        if (organizationId == null) {
            throw LedgerException.badRequest("NO_ACTIVE_ORGANIZATION", "Select or create an organization first");
        }
        return organizationId;
    }

    public void requireAnyRole(OrgRole... allowed) {
        requireOrganizationId();
        if (role == null || Arrays.stream(allowed).noneMatch(r -> r == role)) {
            throw LedgerException.forbidden("Your role (" + role + ") does not permit this operation");
        }
    }

    public void requireWriteAccounting() {
        requireAnyRole(OrgRole.OWNER, OrgRole.ADMIN, OrgRole.ACCOUNTANT);
    }

    public void requireFinanceOperations() {
        requireAnyRole(OrgRole.OWNER, OrgRole.ADMIN, OrgRole.ACCOUNTANT, OrgRole.FINANCE_OFFICER);
    }

    public void requireApprovalAuthority() {
        requireAnyRole(OrgRole.OWNER, OrgRole.ADMIN, OrgRole.APPROVER, OrgRole.ACCOUNTANT);
    }

    public void requireOrgAdmin() {
        requireAnyRole(OrgRole.OWNER, OrgRole.ADMIN);
    }
}
