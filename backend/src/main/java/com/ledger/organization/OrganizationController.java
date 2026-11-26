package com.ledger.organization;

import com.ledger.shared.Currency;
import com.ledger.shared.LedgerPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationService.OrganizationDetailDto create(@Valid @RequestBody OrganizationService.CreateOrganizationRequest req) {
        return organizationService.createOrganization(LedgerPrincipal.current(), req);
    }

    @GetMapping("/current")
    public OrganizationService.OrganizationDetailDto getCurrent() {
        return organizationService.getCurrentOrganization(LedgerPrincipal.current().requireOrganizationId());
    }

    @PatchMapping("/current")
    public OrganizationService.OrganizationDetailDto updateCurrent(@Valid @RequestBody OrganizationService.UpdateOrganizationRequest req) {
        return organizationService.updateOrganization(LedgerPrincipal.current(), req);
    }

    @GetMapping("/current/members")
    public List<OrganizationService.MemberDto> listMembers() {
        return organizationService.listMembers(LedgerPrincipal.current().requireOrganizationId());
    }

    @PostMapping("/current/members")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationService.MemberDto addMember(@Valid @RequestBody OrganizationService.AddMemberRequest req) {
        return organizationService.addOrInviteMember(LedgerPrincipal.current(), req);
    }

    @PatchMapping("/current/members/{membershipId}")
    public OrganizationService.MemberDto updateMemberRole(
            @PathVariable UUID membershipId,
            @Valid @RequestBody OrganizationService.UpdateMemberRoleRequest req
    ) {
        return organizationService.updateMemberRole(LedgerPrincipal.current(), membershipId, req);
    }

    @GetMapping("/currencies")
    public List<Currency> listCurrencies() {
        return organizationService.listCurrencies();
    }

    @GetMapping("/current/exchange-rates")
    public List<OrganizationService.ExchangeRateDto> listExchangeRates() {
        return organizationService.listExchangeRates(LedgerPrincipal.current().requireOrganizationId());
    }

    @PostMapping("/current/exchange-rates")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationService.ExchangeRateDto upsertExchangeRate(
            @Valid @RequestBody OrganizationService.UpsertExchangeRateRequest req
    ) {
        return organizationService.upsertExchangeRate(LedgerPrincipal.current(), req);
    }
}
