package com.ledger.expense;

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
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    public List<ExpenseService.ExpenseDto> list(
            @RequestParam(required = false) ExpenseType type,
            @RequestParam(required = false) ExpenseStatus status
    ) {
        return expenseService.listExpenses(LedgerPrincipal.current().requireOrganizationId(), type, status);
    }

    @GetMapping("/{expenseId}")
    public ExpenseService.ExpenseDto get(@PathVariable UUID expenseId) {
        return expenseService.getExpense(LedgerPrincipal.current().requireOrganizationId(), expenseId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseService.ExpenseDto create(@Valid @RequestBody ExpenseService.CreateExpenseRequest req) {
        return expenseService.createExpense(LedgerPrincipal.current(), req);
    }

    @PostMapping("/{expenseId}/post")
    public ExpenseService.ExpenseDto post(@PathVariable UUID expenseId) {
        return expenseService.postExpense(LedgerPrincipal.current(), expenseId);
    }

    @PostMapping("/{expenseId}/reverse")
    public ExpenseService.ExpenseDto reverse(
            @PathVariable UUID expenseId,
            @RequestBody(required = false) ReverseExpenseRequest req
    ) {
        return expenseService.reverseExpense(LedgerPrincipal.current(), expenseId, req != null ? req.reason() : null);
    }

    @GetMapping("/categories")
    public List<ExpenseService.ExpenseCategoryDto> listCategories() {
        return expenseService.listCategories(LedgerPrincipal.current().requireOrganizationId());
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseService.ExpenseCategoryDto createCategory(@Valid @RequestBody CreateCategoryRequest req) {
        return expenseService.createCategory(LedgerPrincipal.current(), req.name(), req.defaultAccountId());
    }

    public record ReverseExpenseRequest(String reason) {}
    public record CreateCategoryRequest(@NotBlank String name, @NotNull UUID defaultAccountId) {}
}
