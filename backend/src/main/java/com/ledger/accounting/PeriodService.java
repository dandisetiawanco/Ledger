package com.ledger.accounting;

import com.ledger.audit.AuditService;
import com.ledger.journal.JournalEntryRepository;
import com.ledger.journal.JournalStatus;
import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationService;
import com.ledger.shared.DomainEvents;
import com.ledger.shared.LedgerException;
import com.ledger.shared.LedgerPrincipal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PeriodService {

    private final FiscalYearRepository fiscalYearRepository;
    private final AccountingPeriodRepository periodRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AccountingInitializer accountingInitializer;
    private final OrganizationService organizationService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public PeriodService(
            FiscalYearRepository fiscalYearRepository,
            AccountingPeriodRepository periodRepository,
            JournalEntryRepository journalEntryRepository,
            AccountingInitializer accountingInitializer,
            OrganizationService organizationService,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher) {
        this.fiscalYearRepository = fiscalYearRepository;
        this.periodRepository = periodRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.accountingInitializer = accountingInitializer;
        this.organizationService = organizationService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<FiscalYearDto> listFiscalYears(UUID organizationId) {
        return fiscalYearRepository.findByOrganizationIdOrderByStartDateDesc(organizationId).stream()
                .map(fy -> new FiscalYearDto(
                        fy.getId(), fy.getName(), fy.getStartDate(), fy.getEndDate(), fy.isClosed(),
                        periodRepository.findByFiscalYearIdOrderByPeriodNumberAsc(fy.getId()).stream()
                                .map(PeriodDto::from).toList()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PeriodDto> listPeriods(UUID organizationId) {
        return periodRepository.findByOrganizationIdOrderByStartDateAsc(organizationId).stream()
                .map(PeriodDto::from)
                .toList();
    }

    @Transactional
    public FiscalYearDto createFiscalYear(LedgerPrincipal principal, int startYear) {
        principal.requireOrgAdmin();
        UUID orgId = principal.requireOrganizationId();
        Organization org = organizationService.getOrganizationEntity(orgId);
        FiscalYear fy = accountingInitializer.createFiscalYearWithPeriods(orgId, startYear, org.getFiscalYearStartMonth());
        auditService.record(orgId, "FISCAL_YEAR_CREATED", "FISCAL_YEAR", fy.getId().toString(),
                "Created fiscal year " + fy.getName(), Map.of());
        return new FiscalYearDto(
                fy.getId(), fy.getName(), fy.getStartDate(), fy.getEndDate(), fy.isClosed(),
                periodRepository.findByFiscalYearIdOrderByPeriodNumberAsc(fy.getId()).stream()
                        .map(PeriodDto::from).toList()
        );
    }

    @Transactional
    public AccountingPeriod requireOpenPeriodForDate(UUID organizationId, LocalDate entryDate) {
        AccountingPeriod period = periodRepository.findContainingDate(organizationId, entryDate)
                .orElseThrow(() -> LedgerException.businessRule(
                        "PERIOD_NOT_FOUND",
                        "No accounting period covers date " + entryDate + ". Create the fiscal year first."
                ));
        if (period.getStatus() == PeriodStatus.CLOSED) {
            throw LedgerException.businessRule(
                    "PERIOD_CLOSED",
                    "Accounting period " + period.getName() + " is closed and does not accept postings"
            );
        }
        return period;
    }

    @Transactional
    public PeriodDto closePeriod(LedgerPrincipal principal, UUID periodId) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();

        AccountingPeriod period = periodRepository.findByIdAndOrganizationIdForUpdate(periodId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Accounting period not found"));
        if (period.getStatus() == PeriodStatus.CLOSED) {
            throw LedgerException.businessRule("PERIOD_ALREADY_CLOSED", "Accounting period is already closed");
        }

        // Prevent closing a period while unposted drafts or pending approval journals remain inside it.
        long pendingCount = journalEntryRepository.countByOrganizationIdAndAccountingPeriodIdAndStatusIn(
                orgId, periodId, List.of(JournalStatus.DRAFT, JournalStatus.PENDING_APPROVAL)
        );
        if (pendingCount > 0) {
            throw LedgerException.businessRule(
                    "UNPOSTED_JOURNALS_IN_PERIOD",
                    "Resolve or post " + pendingCount + " draft/pending journal entries before closing " + period.getName()
            );
        }

        period.close(principal.userId());
        periodRepository.save(period);

        auditService.record(orgId, "PERIOD_CLOSED", "ACCOUNTING_PERIOD", period.getId().toString(),
                "Closed accounting period " + period.getName(), Map.of());
        eventPublisher.publishEvent(new DomainEvents.PeriodClosedEvent(orgId, period.getId(), period.getName(), principal.userId()));
        return PeriodDto.from(period);
    }

    @Transactional
    public PeriodDto reopenPeriod(LedgerPrincipal principal, UUID periodId, String reason) {
        principal.requireOrgAdmin();
        UUID orgId = principal.requireOrganizationId();
        if (reason == null || reason.isBlank()) {
            throw LedgerException.badRequest("REOPEN_REASON_REQUIRED", "A written reason is required to reopen a closed period");
        }

        AccountingPeriod period = periodRepository.findByIdAndOrganizationIdForUpdate(periodId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Accounting period not found"));
        if (period.getStatus() == PeriodStatus.OPEN) {
            throw LedgerException.businessRule("PERIOD_ALREADY_OPEN", "Accounting period is already open");
        }

        period.reopen(principal.userId(), reason.trim());
        periodRepository.save(period);

        auditService.record(orgId, "PERIOD_REOPENED", "ACCOUNTING_PERIOD", period.getId().toString(),
                "Reopened accounting period " + period.getName() + ": " + reason.trim(),
                Map.of("reason", reason.trim()));
        return PeriodDto.from(period);
    }

    public record PeriodDto(
            UUID id,
            UUID fiscalYearId,
            String name,
            int periodNumber,
            LocalDate startDate,
            LocalDate endDate,
            PeriodStatus status,
            Instant closedAt,
            Instant reopenedAt,
            String reopenReason
    ) {
        static PeriodDto from(AccountingPeriod p) {
            return new PeriodDto(
                    p.getId(), p.getFiscalYearId(), p.getName(), p.getPeriodNumber(),
                    p.getStartDate(), p.getEndDate(), p.getStatus(),
                    p.getClosedAt(), p.getReopenedAt(), p.getReopenReason()
            );
        }
    }

    public record FiscalYearDto(
            UUID id,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            boolean closed,
            List<PeriodDto> periods
    ) {}
}
