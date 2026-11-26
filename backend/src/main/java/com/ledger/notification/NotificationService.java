package com.ledger.notification;

import com.ledger.invoice.Invoice;
import com.ledger.invoice.InvoiceRepository;
import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationRepository;
import com.ledger.shared.DomainEvents;
import com.ledger.shared.LedgerException;
import com.ledger.shared.LedgerPrincipal;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final OrganizationRepository organizationRepository;
    private final InvoiceRepository invoiceRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            OrganizationRepository organizationRepository,
            InvoiceRepository invoiceRepository) {
        this.notificationRepository = notificationRepository;
        this.organizationRepository = organizationRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public void createIdempotent(UUID organizationId, UUID userId, String category, String title,
                                 String message, String entityType, UUID entityId, String dedupKey) {
        if (dedupKey != null && notificationRepository.existsByOrganizationIdAndDedupKey(organizationId, dedupKey)) {
            return;
        }
        Notification n = new Notification(organizationId, userId, category, title, message, entityType, entityId, dedupKey);
        notificationRepository.save(n);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onApprovalRequested(DomainEvents.ApprovalRequestedEvent event) {
        createIdempotent(
                event.organizationId(), null, "APPROVAL_REQUESTED",
                "Approval requested for " + event.reference(),
                event.entityType() + " " + event.reference() + " (" + event.currencyCode() + " " + event.amount().toPlainString() + ") requires review.",
                event.entityType(), event.entityId(),
                "APPROVAL_REQ:" + event.approvalId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onApprovalDecided(DomainEvents.ApprovalDecidedEvent event) {
        createIdempotent(
                event.organizationId(), event.requesterId(), "APPROVAL_DECIDED",
                event.reference() + " was " + event.decision().toLowerCase(),
                "Approval decision recorded for " + event.entityType() + " " + event.reference() + ".",
                event.entityType(), event.entityId(),
                "APPROVAL_DEC:" + event.approvalId() + ":" + event.decision()
        );
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void scanDueAndOverdueInvoices() {
        LocalDate today = LocalDate.now();
        LocalDate soonThreshold = today.plusDays(7);
        for (Organization org : organizationRepository.findAll()) {
            for (Invoice inv : invoiceRepository.findOpenReceivables(org.getId())) {
                if (inv.getDueDate().isBefore(today)) {
                    createIdempotent(
                            org.getId(), null, "INVOICE_OVERDUE",
                            "Invoice " + inv.getInvoiceNumber() + " is overdue",
                            "Invoice " + inv.getInvoiceNumber() + " was due on " + inv.getDueDate() + ".",
                            "INVOICE", inv.getId(),
                            "OVERDUE:" + inv.getId() + ":" + today.getYear() + "-" + today.getMonthValue()
                    );
                } else if (!inv.getDueDate().isAfter(soonThreshold)) {
                    createIdempotent(
                            org.getId(), null, "INVOICE_DUE_SOON",
                            "Invoice " + inv.getInvoiceNumber() + " due soon",
                            "Invoice " + inv.getInvoiceNumber() + " is due on " + inv.getDueDate() + ".",
                            "INVOICE", inv.getId(),
                            "DUE_SOON:" + inv.getId()
                    );
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> listForOrg(UUID organizationId) {
        return notificationRepository.findTop50ByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(NotificationDto::from)
                .toList();
    }

    @Transactional
    public void markRead(LedgerPrincipal principal, UUID notificationId) {
        UUID orgId = principal.requireOrganizationId();
        Notification n = notificationRepository.findByIdAndOrganizationId(notificationId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Notification not found"));
        n.markRead();
        notificationRepository.save(n);
    }

    public record NotificationDto(
            UUID id,
            String category,
            String title,
            String message,
            String entityType,
            UUID entityId,
            Instant readAt,
            Instant createdAt
    ) {
        static NotificationDto from(Notification n) {
            return new NotificationDto(
                    n.getId(), n.getCategory(), n.getTitle(), n.getMessage(),
                    n.getEntityType(), n.getEntityId(), n.getReadAt(), n.getCreatedAt()
            );
        }
    }
}
