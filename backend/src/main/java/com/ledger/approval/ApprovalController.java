package com.ledger.approval;

import com.ledger.shared.LedgerPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    public List<ApprovalService.ApprovalDto> list(@RequestParam(required = false) String status) {
        return approvalService.list(LedgerPrincipal.current().requireOrganizationId(), status);
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalService.ApprovalDto submit(@Valid @RequestBody SubmitApprovalRequest req) {
        return approvalService.submitForApproval(LedgerPrincipal.current(), req.entityType(), req.entityId());
    }

    @PostMapping("/{approvalId}/decide")
    public ApprovalService.ApprovalDto decide(
            @PathVariable UUID approvalId,
            @Valid @RequestBody DecisionRequest req
    ) {
        return approvalService.decide(LedgerPrincipal.current(), approvalId, req.decision(), req.reason());
    }

    public record SubmitApprovalRequest(@NotBlank String entityType, @NotNull UUID entityId) {}
    public record DecisionRequest(@NotBlank String decision, String reason) {}
}
