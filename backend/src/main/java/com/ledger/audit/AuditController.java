package com.ledger.audit;

import com.ledger.shared.LedgerPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public Page<AuditEventDto> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        LedgerPrincipal principal = LedgerPrincipal.current();
        UUID orgId = principal.requireOrganizationId();
        int safeSize = Math.min(Math.max(size, 1), 200);
        return auditService.list(orgId, action, entityType, search, PageRequest.of(Math.max(page, 0), safeSize))
                .map(AuditEventDto::from);
    }

    public record AuditEventDto(
            UUID id,
            UUID actorUserId,
            String actorEmail,
            String action,
            String entityType,
            String entityId,
            String requestId,
            String summary,
            String metadataJson,
            Instant occurredAt
    ) {
        static AuditEventDto from(AuditEvent e) {
            return new AuditEventDto(
                    e.getId(), e.getActorUserId(), e.getActorEmail(), e.getAction(),
                    e.getEntityType(), e.getEntityId(), e.getRequestId(),
                    e.getSummary(), e.getMetadataJson(), e.getOccurredAt()
            );
        }
    }
}
