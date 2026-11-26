package com.ledger.journal;

import com.ledger.shared.LedgerPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class JournalController {

    private final JournalService journalService;
    private final GeneralLedgerService generalLedgerService;

    public JournalController(JournalService journalService, GeneralLedgerService generalLedgerService) {
        this.journalService = journalService;
        this.generalLedgerService = generalLedgerService;
    }

    @GetMapping("/journals")
    public Page<JournalService.JournalEntryDto> listJournals(
            @RequestParam(required = false) JournalStatus status,
            @RequestParam(required = false) JournalSourceType sourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        UUID orgId = LedgerPrincipal.current().requireOrganizationId();
        return journalService.search(orgId, status, sourceType, fromDate, toDate, search,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @GetMapping("/journals/{journalId}")
    public JournalService.JournalEntryDto getJournal(@PathVariable UUID journalId) {
        UUID orgId = LedgerPrincipal.current().requireOrganizationId();
        return journalService.getById(orgId, journalId);
    }

    @PostMapping("/journals")
    @ResponseStatus(HttpStatus.CREATED)
    public JournalService.JournalEntryDto createJournal(@Valid @RequestBody JournalService.CreateJournalRequest req) {
        return journalService.createManualJournal(LedgerPrincipal.current(), req);
    }

    @PatchMapping("/journals/{journalId}")
    public JournalService.JournalEntryDto updateJournal(
            @PathVariable UUID journalId,
            @Valid @RequestBody JournalService.UpdateJournalRequest req
    ) {
        return journalService.updateDraftJournal(LedgerPrincipal.current(), journalId, req);
    }

    @PostMapping("/journals/{journalId}/post")
    public JournalService.JournalEntryDto postJournal(@PathVariable UUID journalId) {
        return journalService.postJournal(LedgerPrincipal.current(), journalId);
    }

    @PostMapping("/journals/{journalId}/reverse")
    public JournalService.JournalEntryDto reverseJournal(
            @PathVariable UUID journalId,
            @RequestBody(required = false) ReverseJournalRequest req
    ) {
        LocalDate revDate = req != null ? req.reversalDate() : null;
        String reason = req != null ? req.reason() : null;
        return journalService.reverseJournal(LedgerPrincipal.current(), journalId, revDate, reason);
    }

    @DeleteMapping("/journals/{journalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraftJournal(@PathVariable UUID journalId) {
        journalService.deleteDraftJournal(LedgerPrincipal.current(), journalId);
    }

    @GetMapping("/general-ledger")
    public GeneralLedgerService.GeneralLedgerResponse generalLedger(
            @RequestParam UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID periodId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        UUID orgId = LedgerPrincipal.current().requireOrganizationId();
        return generalLedgerService.getLedger(orgId, accountId, fromDate, toDate, periodId, search, page, size);
    }

    public record ReverseJournalRequest(LocalDate reversalDate, String reason) {}
}
