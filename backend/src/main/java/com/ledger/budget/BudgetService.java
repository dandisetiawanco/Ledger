package com.ledger.budget;

import com.ledger.accounting.Account;
import com.ledger.accounting.AccountService;
import com.ledger.audit.AuditService;
import com.ledger.journal.JournalLineRepository;
import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationService;
import com.ledger.shared.LedgerException;
import com.ledger.shared.LedgerPrincipal;
import com.ledger.shared.MoneyUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final BudgetRevisionRepository revisionRepository;
    private final AccountService accountService;
    private final OrganizationService organizationService;
    private final JournalLineRepository journalLineRepository;
    private final AuditService auditService;

    public BudgetService(
            BudgetRepository budgetRepository,
            BudgetRevisionRepository revisionRepository,
            AccountService accountService,
            OrganizationService organizationService,
            JournalLineRepository journalLineRepository,
            AuditService auditService) {
        this.budgetRepository = budgetRepository;
        this.revisionRepository = revisionRepository;
        this.accountService = accountService;
        this.organizationService = organizationService;
        this.journalLineRepository = journalLineRepository;
        this.auditService = auditService;
    }

    @Transactional
    public BudgetDetailDto createBudget(LedgerPrincipal principal, CreateBudgetRequest req) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();
        Organization org = organizationService.getOrganizationEntity(orgId);

        if (req.endDate().isBefore(req.startDate())) {
            throw LedgerException.badRequest("INVALID_DATE_RANGE", "Budget end date cannot precede start date");
        }
        String periodType = (req.periodType() != null && req.periodType().equalsIgnoreCase("MONTHLY")) ? "MONTHLY" : "ANNUAL";
        String ccy = org.getBaseCurrency();

        Budget budget = new Budget(
                orgId, req.name(), req.fiscalYearId(), periodType,
                req.startDate(), req.endDate(), ccy, req.notes(), principal.userId()
        );
        attachLines(budget, orgId, ccy, req.lines());
        budgetRepository.save(budget);

        revisionRepository.save(new BudgetRevision(orgId, budget.getId(), 1, "Initial budget created", principal.userId()));
        auditService.record(orgId, "BUDGET_CREATED", "BUDGET", budget.getId().toString(),
                "Created budget " + budget.getName(), Map.of("lines", budget.getLines().size()));

        return evaluateBudget(budget);
    }

    @Transactional
    public BudgetDetailDto updateBudget(LedgerPrincipal principal, UUID budgetId, UpdateBudgetRequest req) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();
        Budget budget = budgetRepository.findByIdAndOrganizationId(budgetId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Budget not found"));

        if (req.name() != null && !req.name().isBlank()) budget.setName(req.name());
        if (req.notes() != null) budget.setNotes(req.notes());
        if (req.lines() != null) {
            budget.clearLines();
            attachLines(budget, orgId, budget.getCurrencyCode(), req.lines());
        }
        budget.incrementRevision();
        budgetRepository.save(budget);

        String summary = (req.changeSummary() != null && !req.changeSummary().isBlank())
                ? req.changeSummary().trim()
                : "Updated budget allocations (revision " + budget.getRevisionNumber() + ")";
        revisionRepository.save(new BudgetRevision(orgId, budget.getId(), budget.getRevisionNumber(), summary, principal.userId()));
        auditService.record(orgId, "BUDGET_REVISED", "BUDGET", budget.getId().toString(), summary, Map.of("revision", budget.getRevisionNumber()));
        return evaluateBudget(budget);
    }

    @Transactional(readOnly = true)
    public List<BudgetDetailDto> listBudgets(UUID organizationId) {
        return budgetRepository.findByOrganizationIdOrderByStartDateDesc(organizationId).stream()
                .map(this::evaluateBudget)
                .toList();
    }

    @Transactional(readOnly = true)
    public BudgetDetailDto getBudget(UUID organizationId, UUID budgetId) {
        Budget b = budgetRepository.findByIdAndOrganizationId(budgetId, organizationId)
                .orElseThrow(() -> LedgerException.notFound("Budget not found"));
        return evaluateBudget(b);
    }

    private void attachLines(Budget budget, UUID orgId, String ccy, List<BudgetLineInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw LedgerException.badRequest("LINES_REQUIRED", "Budget requires at least one account line");
        }
        for (BudgetLineInput in : inputs) {
            Account acc = accountService.requirePostingAccount(orgId, in.accountId());
            if (in.plannedAmount() == null || MoneyUtil.isNegative(in.plannedAmount())) {
                throw LedgerException.badRequest("INVALID_PLANNED_AMOUNT", "Planned budget amount cannot be negative");
            }
            budget.addLine(new BudgetLine(orgId, acc.getId(), in.periodMonth(), MoneyUtil.round(in.plannedAmount(), ccy), in.notes()));
        }
    }

    private BudgetDetailDto evaluateBudget(Budget budget) {
        UUID orgId = budget.getOrganizationId();
        Map<UUID, BigDecimal[]> actualsByAccount = new HashMap<>();
        for (Object[] row : journalLineRepository.sumPostedByAccountInRange(orgId, budget.getStartDate(), budget.getEndDate())) {
            actualsByAccount.put((UUID) row[0], new BigDecimal[]{(BigDecimal) row[1], (BigDecimal) row[2]});
        }

        BigDecimal totalPlanned = MoneyUtil.ZERO;
        BigDecimal totalActual = MoneyUtil.ZERO;
        List<BudgetLinePerformanceDto> lineDtos = new ArrayList<>();

        for (BudgetLine line : budget.getLines()) {
            Account acc = accountService.requireAccount(orgId, line.getAccountId());
            BigDecimal[] sums = actualsByAccount.getOrDefault(line.getAccountId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal actual = MoneyUtil.normalize(acc.getNormalBalance().netChange(sums[0], sums[1]));
            BigDecimal planned = MoneyUtil.normalize(line.getPlannedAmount());
            BigDecimal variance = planned.subtract(actual);

            // Handle zero-budget lines safely without division by zero.
            String utilizationPct;
            if (MoneyUtil.isZero(planned)) {
                utilizationPct = MoneyUtil.isZero(actual) ? "0.00" : "N/A";
            } else {
                BigDecimal pct = actual.multiply(MoneyUtil.ONE_HUNDRED).divide(planned, 2, RoundingMode.HALF_UP);
                utilizationPct = pct.toPlainString();
            }

            totalPlanned = totalPlanned.add(planned);
            totalActual = totalActual.add(actual);

            lineDtos.add(new BudgetLinePerformanceDto(
                    line.getId(), acc.getId(), acc.getCode(), acc.getName(), acc.getAccountType().name(),
                    line.getPeriodMonth(), MoneyUtil.toMoneyString(planned), MoneyUtil.toMoneyString(actual),
                    MoneyUtil.toMoneyString(variance), utilizationPct, line.getNotes()
            ));
        }

        BigDecimal totalVariance = totalPlanned.subtract(totalActual);
        String overallUtilization = MoneyUtil.isZero(totalPlanned)
                ? (MoneyUtil.isZero(totalActual) ? "0.00" : "N/A")
                : totalActual.multiply(MoneyUtil.ONE_HUNDRED).divide(totalPlanned, 2, RoundingMode.HALF_UP).toPlainString();

        List<BudgetRevisionDto> revisions = revisionRepository
                .findByOrganizationIdAndBudgetIdOrderByRevisionNumberDesc(orgId, budget.getId())
                .stream()
                .map(r -> new BudgetRevisionDto(r.getId(), r.getRevisionNumber(), r.getChangeSummary(), r.getRevisedAt()))
                .toList();

        return new BudgetDetailDto(
                budget.getId(), budget.getName(), budget.getPeriodType(),
                budget.getStartDate(), budget.getEndDate(), budget.getCurrencyCode(),
                budget.getNotes(), budget.getRevisionNumber(),
                MoneyUtil.toMoneyString(totalPlanned), MoneyUtil.toMoneyString(totalActual),
                MoneyUtil.toMoneyString(totalVariance), overallUtilization,
                lineDtos, revisions
        );
    }

    public record BudgetLineInput(
            @jakarta.validation.constraints.NotNull UUID accountId,
            Integer periodMonth,
            @jakarta.validation.constraints.NotNull BigDecimal plannedAmount,
            String notes
    ) {}

    public record CreateBudgetRequest(
            @jakarta.validation.constraints.NotBlank String name,
            UUID fiscalYearId,
            String periodType,
            @jakarta.validation.constraints.NotNull LocalDate startDate,
            @jakarta.validation.constraints.NotNull LocalDate endDate,
            String notes,
            @jakarta.validation.constraints.NotEmpty List<BudgetLineInput> lines
    ) {}

    public record UpdateBudgetRequest(
            String name,
            String notes,
            String changeSummary,
            List<BudgetLineInput> lines
    ) {}

    public record BudgetLinePerformanceDto(
            UUID id,
            UUID accountId,
            String accountCode,
            String accountName,
            String accountType,
            Integer periodMonth,
            String plannedAmount,
            String actualAmount,
            String variance,
            String utilizationPercent,
            String notes
    ) {}

    public record BudgetRevisionDto(UUID id, int revisionNumber, String changeSummary, Instant revisedAt) {}

    public record BudgetDetailDto(
            UUID id,
            String name,
            String periodType,
            LocalDate startDate,
            LocalDate endDate,
            String currencyCode,
            String notes,
            int revisionNumber,
            String totalPlanned,
            String totalActual,
            String totalVariance,
            String overallUtilizationPercent,
            List<BudgetLinePerformanceDto> lines,
            List<BudgetRevisionDto> revisions
    ) {}
}
