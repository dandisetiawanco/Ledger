package com.ledger.budget;

import com.ledger.shared.LedgerPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public List<BudgetService.BudgetDetailDto> list() {
        return budgetService.listBudgets(LedgerPrincipal.current().requireOrganizationId());
    }

    @GetMapping("/{budgetId}")
    public BudgetService.BudgetDetailDto get(@PathVariable UUID budgetId) {
        return budgetService.getBudget(LedgerPrincipal.current().requireOrganizationId(), budgetId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetService.BudgetDetailDto create(@Valid @RequestBody BudgetService.CreateBudgetRequest req) {
        return budgetService.createBudget(LedgerPrincipal.current(), req);
    }

    @PatchMapping("/{budgetId}")
    public BudgetService.BudgetDetailDto update(
            @PathVariable UUID budgetId,
            @Valid @RequestBody BudgetService.UpdateBudgetRequest req
    ) {
        return budgetService.updateBudget(LedgerPrincipal.current(), budgetId, req);
    }
}
