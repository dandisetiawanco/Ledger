package com.ledger.reconciliation;

import com.ledger.shared.LedgerPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/bank-accounts/{bankAccountId}")
    public ReconciliationService.ReconciliationWorkspaceDto workspace(@PathVariable UUID bankAccountId) {
        return reconciliationService.getWorkspace(LedgerPrincipal.current().requireOrganizationId(), bankAccountId);
    }

    @PostMapping("/match")
    @ResponseStatus(HttpStatus.CREATED)
    public ReconciliationService.ReconciliationHistoryDto confirmMatch(
            @Valid @RequestBody ReconciliationService.ConfirmMatchRequest req
    ) {
        return reconciliationService.confirmMatch(LedgerPrincipal.current(), req);
    }

    @PostMapping("/create-journal-and-match")
    @ResponseStatus(HttpStatus.CREATED)
    public ReconciliationService.ReconciliationHistoryDto createJournalAndMatch(
            @Valid @RequestBody ReconciliationService.CreateAdjustmentReconcileRequest req
    ) {
        return reconciliationService.createJournalAndReconcile(LedgerPrincipal.current(), req);
    }

    @PostMapping("/{reconciliationId}/unmatch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unmatch(@PathVariable UUID reconciliationId) {
        reconciliationService.unmatch(LedgerPrincipal.current(), reconciliationId);
    }
}
