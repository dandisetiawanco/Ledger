package com.ledger.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.shared.LedgerPrincipal;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(UUID organizationId, String action, String entityType, String entityId,
                       String summary, Map<String, Object> metadata) {
        UUID actorId = null;
        String actorEmail = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LedgerPrincipal principal) {
            actorId = principal.userId();
            actorEmail = principal.email();
        }
        recordWithActor(organizationId, actorId, actorEmail, action, entityType, entityId, summary, metadata);
    }

    @Transactional
    public void recordWithActor(UUID organizationId, UUID actorId, String actorEmail, String action,
                                String entityType, String entityId, String summary, Map<String, Object> metadata) {
        String json = null;
        if (metadata != null && !metadata.isEmpty()) {
            try {
                json = objectMapper.writeValueAsString(metadata);
            } catch (JsonProcessingException ignored) {
                json = "{}";
            }
        }
        AuditEvent event = new AuditEvent(
                organizationId,
                actorId,
                actorEmail,
                action,
                entityType,
                entityId,
                MDC.get("requestId"),
                summary,
                json
        );
        repository.save(event);
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> list(UUID organizationId, String action, String entityType, String search, Pageable pageable) {
        String cleanAction = (action == null || action.isBlank()) ? null : action.trim();
        String cleanEntity = (entityType == null || entityType.isBlank()) ? null : entityType.trim();
        String cleanSearch = (search == null || search.isBlank()) ? null : search.trim();
        return repository.searchByOrganization(organizationId, cleanAction, cleanEntity, cleanSearch, pageable);
    }
}
