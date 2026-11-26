package com.ledger.accounting;

import com.ledger.audit.AuditService;
import com.ledger.journal.JournalLineRepository;
import com.ledger.shared.LedgerException;
import com.ledger.shared.LedgerPrincipal;
import com.ledger.shared.MoneyUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final JournalLineRepository journalLineRepository;
    private final AuditService auditService;

    public AccountService(AccountRepository accountRepository,
                          JournalLineRepository journalLineRepository,
                          AuditService auditService) {
        this.accountRepository = accountRepository;
        this.journalLineRepository = journalLineRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AccountDto> listAccounts(UUID organizationId, LocalDate asOfDate, boolean includeArchived) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now().plusYears(10);
        List<Account> accounts = includeArchived
                ? accountRepository.findByOrganizationIdOrderByCodeAsc(organizationId)
                : accountRepository.findByOrganizationIdAndArchivedFalseOrderByCodeAsc(organizationId);

        Map<UUID, BigDecimal[]> totalsByAccount = new HashMap<>();
        for (Object[] row : journalLineRepository.sumPostedByAccountUpTo(organizationId, effectiveDate)) {
            UUID accId = (UUID) row[0];
            BigDecimal debit = (BigDecimal) row[1];
            BigDecimal credit = (BigDecimal) row[2];
            totalsByAccount.put(accId, new BigDecimal[]{debit, credit});
        }

        return accounts.stream().map(a -> {
            BigDecimal[] sums = totalsByAccount.getOrDefault(a.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal balance = a.getNormalBalance().netChange(sums[0], sums[1]);
            return AccountDto.from(a, sums[0], sums[1], balance);
        }).toList();
    }

    @Transactional(readOnly = true)
    public Account requireAccount(UUID organizationId, UUID accountId) {
        return accountRepository.findByIdAndOrganizationId(accountId, organizationId)
                .orElseThrow(() -> LedgerException.notFound("Account not found in organization"));
    }

    @Transactional(readOnly = true)
    public Account requirePostingAccount(UUID organizationId, UUID accountId) {
        Account account = requireAccount(organizationId, accountId);
        if (!account.isActive() || account.isArchived()) {
            throw LedgerException.businessRule("INACTIVE_ACCOUNT", "Account " + account.getCode() + " is inactive or archived");
        }
        if (!account.isAllowPosting()) {
            throw LedgerException.businessRule("NON_POSTING_ACCOUNT", "Cannot post directly to header account " + account.getCode());
        }
        return account;
    }

    @Transactional(readOnly = true)
    public Account requireBySystemCode(UUID organizationId, String systemCode) {
        return accountRepository.findByOrganizationIdAndSystemCode(organizationId, systemCode)
                .orElseThrow(() -> LedgerException.businessRule("SYSTEM_ACCOUNT_MISSING", "Required system account missing: " + systemCode));
    }

    @Transactional
    public AccountDto createAccount(LedgerPrincipal principal, CreateAccountRequest req) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();

        String cleanCode = req.code().trim();
        if (accountRepository.existsByOrganizationIdAndCode(orgId, cleanCode)) {
            throw LedgerException.conflict("DUPLICATE_ACCOUNT_CODE", "Account code already exists: " + cleanCode);
        }
        UUID parentId = null;
        if (req.parentAccountId() != null) {
            Account parent = requireAccount(orgId, req.parentAccountId());
            if (parent.getAccountType() != req.accountType()) {
                throw LedgerException.businessRule("PARENT_TYPE_MISMATCH", "Parent account must share the same classification");
            }
            parentId = parent.getId();
        }
        NormalBalance normal = req.normalBalance() != null ? req.normalBalance() : req.accountType().defaultNormalBalance();
        CashFlowCategory cf = req.cashFlowCategory() != null ? req.cashFlowCategory() : defaultCashFlowCategory(req.accountType());
        boolean allowPosting = req.allowPosting() == null || req.allowPosting();

        Account created = new Account(
                orgId, cleanCode, req.name(), req.accountType(), normal,
                parentId, null, cf, req.description(), req.currencyCode(), allowPosting
        );
        accountRepository.save(created);

        auditService.record(orgId, "ACCOUNT_CREATED", "ACCOUNT", created.getId().toString(),
                "Created account " + created.getCode() + " " + created.getName(),
                Map.of("type", created.getAccountType().name(), "allowPosting", allowPosting));
        return AccountDto.from(created, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional
    public AccountDto updateAccount(LedgerPrincipal principal, UUID accountId, UpdateAccountRequest req) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();
        Account account = requireAccount(orgId, accountId);

        if (req.name() != null && !req.name().isBlank()) account.setName(req.name());
        if (req.description() != null) account.setDescription(req.description());
        if (req.cashFlowCategory() != null) account.setCashFlowCategory(req.cashFlowCategory());
        if (req.active() != null) account.setActive(req.active());
        if (req.allowPosting() != null) {
            if (!req.allowPosting() && journalLineRepository.existsByAccountId(accountId)) {
                throw LedgerException.businessRule("ACCOUNT_HAS_POSTINGS", "Cannot convert an account with postings into a non-posting header");
            }
            account.setAllowPosting(req.allowPosting());
        }
        accountRepository.save(account);

        auditService.record(orgId, "ACCOUNT_UPDATED", "ACCOUNT", account.getId().toString(),
                "Updated account " + account.getCode(), Map.of("active", account.isActive()));
        return AccountDto.from(account, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional
    public void archiveAccount(LedgerPrincipal principal, UUID accountId) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();
        Account account = requireAccount(orgId, accountId);
        if (account.getSystemCode() != null) {
            throw LedgerException.businessRule("SYSTEM_ACCOUNT_PROTECTED", "Core system accounts cannot be archived");
        }
        account.setArchived(true);
        account.setActive(false);
        accountRepository.save(account);
        auditService.record(orgId, "ACCOUNT_ARCHIVED", "ACCOUNT", account.getId().toString(),
                "Archived account " + account.getCode(), Map.of());
    }

    @Transactional
    public void deleteAccount(LedgerPrincipal principal, UUID accountId) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();
        Account account = requireAccount(orgId, accountId);

        if (account.getSystemCode() != null) {
            throw LedgerException.businessRule("SYSTEM_ACCOUNT_PROTECTED", "Core system accounts cannot be deleted");
        }
        if (journalLineRepository.existsByAccountId(accountId)) {
            throw LedgerException.businessRule(
                    "ACCOUNT_IN_USE",
                    "Account " + account.getCode() + " has journal postings and cannot be deleted. Archive it instead."
            );
        }
        if (accountRepository.existsByParentAccountId(accountId)) {
            throw LedgerException.businessRule("ACCOUNT_HAS_CHILDREN", "Remove or reassign child accounts before deleting");
        }
        accountRepository.delete(account);
        auditService.record(orgId, "ACCOUNT_DELETED", "ACCOUNT", accountId.toString(),
                "Deleted unused account " + account.getCode(), Map.of());
    }

    private CashFlowCategory defaultCashFlowCategory(AccountType type) {
        return switch (type) {
            case EQUITY -> CashFlowCategory.FINANCING;
            default -> CashFlowCategory.OPERATING;
        };
    }

    public record CreateAccountRequest(
            @jakarta.validation.constraints.NotBlank String code,
            @jakarta.validation.constraints.NotBlank String name,
            @jakarta.validation.constraints.NotNull AccountType accountType,
            NormalBalance normalBalance,
            UUID parentAccountId,
            CashFlowCategory cashFlowCategory,
            String description,
            String currencyCode,
            Boolean allowPosting
    ) {}

    public record UpdateAccountRequest(
            String name,
            String description,
            CashFlowCategory cashFlowCategory,
            Boolean allowPosting,
            Boolean active
    ) {}

    public record AccountDto(
            UUID id,
            String code,
            String name,
            AccountType accountType,
            NormalBalance normalBalance,
            UUID parentAccountId,
            String systemCode,
            CashFlowCategory cashFlowCategory,
            String description,
            String currencyCode,
            boolean allowPosting,
            boolean active,
            boolean archived,
            String totalDebit,
            String totalCredit,
            String balance
    ) {
        static AccountDto from(Account a, BigDecimal debit, BigDecimal credit, BigDecimal balance) {
            return new AccountDto(
                    a.getId(), a.getCode(), a.getName(), a.getAccountType(), a.getNormalBalance(),
                    a.getParentAccountId(), a.getSystemCode(), a.getCashFlowCategory(), a.getDescription(),
                    a.getCurrencyCode(), a.isAllowPosting(), a.isActive(), a.isArchived(),
                    MoneyUtil.toMoneyString(debit), MoneyUtil.toMoneyString(credit), MoneyUtil.toMoneyString(balance)
            );
        }
    }
}
