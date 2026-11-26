package com.ledger.approval;

import com.ledger.audit.AuditService;
import com.ledger.expense.Expense;
import com.ledger.expense.ExpenseRepository;
import com.ledger.expense.ExpenseStatus;
import com.ledger.expense.ExpenseType;
import com.ledger.identity.User;
import com.ledger.identity.UserRepository;
import com.ledger.invoice.Invoice;
import com.ledger.invoice.InvoiceRepository;
import com.ledger.invoice.InvoiceStatus;
import com.ledger.journal.JournalEntry;
import com.ledger.journal.JournalEntryRepository;
import com.ledger.journal.JournalService;
import com.ledger.organization.FinancialSettings;
import com.ledger.organization.OrganizationService;
import com.ledger.shared.DomainEvents;
import com.ledger.shared.LedgerException;
import com.ledger.shared.LedgerPrincipal;
import com.ledger.shared.MoneyUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ApprovalService {

    private final ApprovalRequestRepository approvalRepository;
    private final ExpenseRepository expenseRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalService journalService;
    private final InvoiceRepository invoiceRepository;
    private final OrganizationService organizationService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public ApprovalService(
            ApprovalRequestRepository approvalRepository,
            ExpenseRepository expenseRepository,
            JournalEntryRepository journalEntryRepository,
            JournalService journalService,
            InvoiceRepository invoiceRepository,
            OrganizationService organizationService,
            UserRepository userRepository,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher) {
        this.approvalRepository = approvalRepository;
        this.expenseRepository = expenseRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.journalService = journalService;
        this.invoiceRepository = invoiceRepository;
        this.organizationService = organizationService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ApprovalDto submitForApproval(LedgerPrincipal principal, String entityType, UUID entityId) {
        principal.requireFinanceOperations();
        UUID orgId = principal.requireOrganizationId();
        String normalizedType = entityType.trim().toUpperCase();

        if (approvalRepository.findFirstByOrganizationIdAndEntityTypeAndEntityIdAndStatus(orgId, normalizedType, entityId, "PENDING").isPresent()) {
            throw LedgerException.conflict("APPROVAL_ALREADY_PENDING", "An approval request is already pending for this item");
        }

        String ref;
        BigDecimal amount;
        String ccy;

        switch (normalizedType) {
            case "EXPENSE", "SUPPLIER_BILL" -> {
                Expense exp = expenseRepository.findByIdAndOrganizationId(entityId, orgId)
                        .orElseThrow(() -> LedgerException.notFound("Expense not found"));
                if (exp.getStatus() != ExpenseStatus.DRAFT && exp.getStatus() != ExpenseStatus.REJECTED) {
                    throw LedgerException.businessRule("INVALID_STATUS", "Only draft or returned expenses can be submitted for approval");
                }
                exp.setStatus(ExpenseStatus.SUBMITTED);
                expenseRepository.save(exp);
                normalizedType = exp.getExpenseType() == ExpenseType.SUPPLIER_BILL ? "SUPPLIER_BILL" : "EXPENSE";
                ref = exp.getExpenseNumber();
                amount = exp.getTotalAmount();
                ccy = exp.getCurrencyCode();
            }
            case "MANUAL_JOURNAL" -> {
                JournalEntry je = journalEntryRepository.findByIdAndOrganizationId(entityId, orgId)
                        .orElseThrow(() -> LedgerException.notFound("Journal entry not found"));
                journalService.markPendingApproval(orgId, entityId);
                ref = je.getEntryNumber();
                amount = je.getTotalDebit();
                ccy = je.getCurrencyCode();
            }
            case "INVOICE" -> {
                Invoice inv = invoiceRepository.findByIdAndOrganizationId(entityId, orgId)
                        .orElseThrow(() -> LedgerException.notFound("Invoice not found"));
                if (inv.getStatus() != InvoiceStatus.DRAFT) {
                    throw LedgerException.businessRule("INVALID_STATUS", "Only draft invoices can be submitted for approval");
                }
                inv.setStatus(InvoiceStatus.PENDING_APPROVAL);
                invoiceRepository.save(inv);
                ref = inv.getInvoiceNumber();
                amount = inv.getTotalAmount();
                ccy = inv.getCurrencyCode();
            }
            default -> throw LedgerException.badRequest("UNSUPPORTED_APPROVAL_TYPE", "Unsupported approval entity type: " + entityType);
        }

        ApprovalRequest req = new ApprovalRequest(orgId, normalizedType, entityId, ref, amount, ccy, principal.userId());
        approvalRepository.save(req);

        auditService.record(orgId, "APPROVAL_SUBMITTED", normalizedType, entityId.toString(),
                "Submitted " + ref + " for approval", Map.of("approvalId", req.getId().toString()));
        eventPublisher.publishEvent(new DomainEvents.ApprovalRequestedEvent(
                orgId, req.getId(), normalizedType, entityId, ref, amount, ccy, principal.userId()));
        return toDto(req);
    }

    @Transactional
    public ApprovalDto decide(LedgerPrincipal principal, UUID approvalId, String decision, String reason) {
        principal.requireApprovalAuthority();
        UUID orgId = principal.requireOrganizationId();

        ApprovalRequest req = approvalRepository.findByIdAndOrganizationId(approvalId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Approval request not found"));
        if (!"PENDING".equals(req.getStatus())) {
            throw LedgerException.businessRule("ALREADY_DECIDED", "Approval request has already been decided");
        }

        String normalizedDecision = decision.trim().toUpperCase();
        if (!List.of("APPROVED", "REJECTED", "RETURNED").contains(normalizedDecision)) {
            throw LedgerException.badRequest("INVALID_DECISION", "Decision must be APPROVED, REJECTED, or RETURNED");
        }

        FinancialSettings settings = organizationService.getSettingsEntity(orgId);
        if ("APPROVED".equals(normalizedDecision)
                && settings.isEnforceSeparationOfDuties()
                && req.getRequesterId().equals(principal.userId())) {
            throw LedgerException.forbidden(
                    "Separation of duties prevents a requester from approving their own transaction"
            );
        }

        req.decide(normalizedDecision, principal.userId(), reason);
        approvalRepository.save(req);

        // Apply workflow state transition to underlying financial document (keeping approval distinct from posting).
        switch (req.getEntityType()) {
            case "EXPENSE", "SUPPLIER_BILL" -> {
                Expense exp = expenseRepository.findByIdAndOrganizationId(req.getEntityId(), orgId).orElseThrow();
                if ("APPROVED".equals(normalizedDecision)) {
                    exp.markApproved(principal.userId());
                } else if ("REJECTED".equals(normalizedDecision)) {
                    exp.setStatus(ExpenseStatus.REJECTED);
                } else {
                    exp.setStatus(ExpenseStatus.DRAFT);
                }
                expenseRepository.save(exp);
            }
            case "MANUAL_JOURNAL" -> journalService.applyApprovalDecision(
                    orgId, req.getEntityId(), principal.userId(), "APPROVED".equals(normalizedDecision)
            );
            case "INVOICE" -> {
                Invoice inv = invoiceRepository.findByIdAndOrganizationId(req.getEntityId(), orgId).orElseThrow();
                inv.setStatus(InvoiceStatus.DRAFT);
                invoiceRepository.save(inv);
            }
        }

        auditService.record(orgId, "APPROVAL_" + normalizedDecision, req.getEntityType(), req.getEntityId().toString(),
                normalizedDecision + " " + req.getEntityReference() + (reason != null && !reason.isBlank() ? ": " + reason : ""),
                Map.of("approvalId", req.getId().toString()));
        eventPublisher.publishEvent(new DomainEvents.ApprovalDecidedEvent(
                orgId, req.getId(), req.getEntityType(), req.getEntityId(), req.getEntityReference(),
                normalizedDecision, req.getRequesterId(), principal.userId()));
        return toDto(req);
    }

    @Transactional(readOnly = true)
    public List<ApprovalDto> list(UUID organizationId, String status) {
        List<ApprovalRequest> list = (status == null || status.isBlank())
                ? approvalRepository.findByOrganizationIdOrderByRequestedAtDesc(organizationId)
                : approvalRepository.findByOrganizationIdAndStatusOrderByRequestedAtDesc(organizationId, status.toUpperCase());
        return list.stream().map(this::toDto).toList();
    }

    private ApprovalDto toDto(ApprovalRequest r) {
        String requesterName = userRepository.findById(r.getRequesterId()).map(User::getFullName).orElse("Unknown");
        String approverName = r.getApproverId() != null
                ? userRepository.findById(r.getApproverId()).map(User::getFullName).orElse("Unknown")
                : null;
        return new ApprovalDto(
                r.getId(), r.getEntityType(), r.getEntityId(), r.getEntityReference(),
                MoneyUtil.toMoneyString(r.getAmount()), r.getCurrencyCode(), r.getStatus(),
                r.getRequesterId(), requesterName, r.getApproverId(), approverName,
                r.getDecisionReason(), r.getRequestedAt(), r.getDecidedAt()
        );
    }

    public record ApprovalDto(
            UUID id,
            String entityType,
            UUID entityId,
            String entityReference,
            String amount,
            String currencyCode,
            String status,
            UUID requesterId,
            String requesterName,
            UUID approverId,
            String approverName,
            String decisionReason,
            Instant requestedAt,
            Instant decidedAt
    ) {}
}
