package com.ledger.accounting;

import com.ledger.shared.LedgerPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AccountingController {

    private final AccountService accountService;
    private final PeriodService periodService;

    public AccountingController(AccountService accountService, PeriodService periodService) {
        this.accountService = accountService;
        this.periodService = periodService;
    }

    @GetMapping("/accounts")
    public List<AccountService.AccountDto> listAccounts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        UUID orgId = LedgerPrincipal.current().requireOrganizationId();
        return accountService.listAccounts(orgId, asOfDate, includeArchived);
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountService.AccountDto createAccount(@Valid @RequestBody AccountService.CreateAccountRequest req) {
        return accountService.createAccount(LedgerPrincipal.current(), req);
    }

    @PatchMapping("/accounts/{accountId}")
    public AccountService.AccountDto updateAccount(
            @PathVariable UUID accountId,
            @Valid @RequestBody AccountService.UpdateAccountRequest req
    ) {
        return accountService.updateAccount(LedgerPrincipal.current(), accountId, req);
    }

    @PostMapping("/accounts/{accountId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveAccount(@PathVariable UUID accountId) {
        accountService.archiveAccount(LedgerPrincipal.current(), accountId);
    }

    @DeleteMapping("/accounts/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable UUID accountId) {
        accountService.deleteAccount(LedgerPrincipal.current(), accountId);
    }

    @GetMapping("/fiscal-years")
    public List<PeriodService.FiscalYearDto> listFiscalYears() {
        return periodService.listFiscalYears(LedgerPrincipal.current().requireOrganizationId());
    }

    @PostMapping("/fiscal-years")
    @ResponseStatus(HttpStatus.CREATED)
    public PeriodService.FiscalYearDto createFiscalYear(@RequestBody CreateFiscalYearRequest req) {
        return periodService.createFiscalYear(LedgerPrincipal.current(), req.startYear());
    }

    @GetMapping("/periods")
    public List<PeriodService.PeriodDto> listPeriods() {
        return periodService.listPeriods(LedgerPrincipal.current().requireOrganizationId());
    }

    @PostMapping("/periods/{periodId}/close")
    public PeriodService.PeriodDto closePeriod(@PathVariable UUID periodId) {
        return periodService.closePeriod(LedgerPrincipal.current(), periodId);
    }

    @PostMapping("/periods/{periodId}/reopen")
    public PeriodService.PeriodDto reopenPeriod(
            @PathVariable UUID periodId,
            @Valid @RequestBody ReopenPeriodRequest req
    ) {
        return periodService.reopenPeriod(LedgerPrincipal.current(), periodId, req.reason());
    }

    public record CreateFiscalYearRequest(int startYear) {}
    public record ReopenPeriodRequest(@NotBlank String reason) {}
}
