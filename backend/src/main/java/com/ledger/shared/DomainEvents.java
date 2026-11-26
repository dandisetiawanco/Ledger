package com.ledger.shared;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain events published synchronously inside financial transactions and handled
 * after-commit for non-critical side effects such as notifications.
 */
public final class DomainEvents {

    private DomainEvents() {
    }

    public record JournalPostedEvent(UUID organizationId, UUID journalEntryId, String entryNumber, LocalDate entryDate, BigDecimal totalAmount) {}

    public record JournalReversedEvent(UUID organizationId, UUID originalJournalId, UUID reversalJournalId, String entryNumber) {}

    public record InvoiceIssuedEvent(UUID organizationId, UUID invoiceId, String invoiceNumber, UUID customerId, BigDecimal totalAmount, String currencyCode, LocalDate dueDate) {}

    public record PaymentRecordedEvent(UUID organizationId, UUID paymentId, String paymentNumber, String paymentType, BigDecimal amount, String currencyCode) {}

    public record ExpensePostedEvent(UUID organizationId, UUID expenseId, String expenseNumber, String expenseType, BigDecimal totalAmount, String currencyCode) {}

    public record PeriodClosedEvent(UUID organizationId, UUID periodId, String periodName, UUID closedBy) {}

    public record ApprovalRequestedEvent(UUID organizationId, UUID approvalId, String entityType, UUID entityId, String reference, BigDecimal amount, String currencyCode, UUID requesterId) {}

    public record ApprovalDecidedEvent(UUID organizationId, UUID approvalId, String entityType, UUID entityId, String reference, String decision, UUID requesterId, UUID approverId) {}
}
