package com.ledger.contact;

import com.ledger.shared.LedgerPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping("/contacts")
    public List<ContactService.ContactSummaryDto> listContacts(@RequestParam(required = false) ContactType type) {
        return contactService.listContacts(LedgerPrincipal.current().requireOrganizationId(), type);
    }

    @GetMapping("/contacts/{contactId}")
    public ContactService.ContactDetailDto getContact(@PathVariable UUID contactId) {
        return contactService.getContactDetail(LedgerPrincipal.current().requireOrganizationId(), contactId);
    }

    @PostMapping("/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public ContactService.ContactSummaryDto createContact(@Valid @RequestBody ContactService.CreateContactRequest req) {
        return contactService.createContact(LedgerPrincipal.current(), req);
    }

    @PatchMapping("/contacts/{contactId}")
    public ContactService.ContactSummaryDto updateContact(
            @PathVariable UUID contactId,
            @Valid @RequestBody ContactService.UpdateContactRequest req
    ) {
        return contactService.updateContact(LedgerPrincipal.current(), contactId, req);
    }

    @GetMapping("/catalog")
    public List<ContactService.CatalogItemDto> listCatalog() {
        return contactService.listCatalog(LedgerPrincipal.current().requireOrganizationId());
    }

    @PostMapping("/catalog")
    @ResponseStatus(HttpStatus.CREATED)
    public ContactService.CatalogItemDto createCatalogItem(@Valid @RequestBody ContactService.CreateCatalogItemRequest req) {
        return contactService.createCatalogItem(LedgerPrincipal.current(), req);
    }

    @PatchMapping("/catalog/{itemId}")
    public ContactService.CatalogItemDto updateCatalogItem(
            @PathVariable UUID itemId,
            @Valid @RequestBody ContactService.UpdateCatalogItemRequest req
    ) {
        return contactService.updateCatalogItem(LedgerPrincipal.current(), itemId, req);
    }
}
