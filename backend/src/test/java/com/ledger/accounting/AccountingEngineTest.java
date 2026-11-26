package com.ledger.accounting;

import com.ledger.identity.User;
import com.ledger.identity.UserRepository;
import com.ledger.journal.JournalEntryRepository;
import com.ledger.journal.JournalService;
import com.ledger.journal.JournalSourceType;
import com.ledger.journal.JournalStatus;
import com.ledger.organization.OrgRole;
import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationService;
import com.ledger.shared.LedgerException;
import com.ledger.shared.LedgerPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AccountingEngineTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OrganizationService organizationService;
    @Autowired private AccountService accountService;
    @Autowired private PeriodService periodService;
    @Autowired private JournalService journalService;
    @Autowired private JournalEntryRepository journalEntryRepository;

    private LedgerPrincipal ownerA;
    private LedgerPrincipal ownerB;
    private Organization orgA;
    private Organization orgB;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User userA = userRepository.save(new User("acct-a-" + suffix + "@test.local", passwordEncoder.encode("Secret123!"), "Owner A", false));
        User userB = userRepository.save(new User("acct-b-" + suffix + "@test.local", passwordEncoder.encode("Secret123!"), "Owner B", false));
        orgA = organizationService.createOrganizationInternal(userA, "Org A " + suffix, "Agency", "IDR", "Asia/Jakarta", "id-ID", 1, false);
        orgB = organizationService.createOrganizationInternal(userB, "Org B " + suffix, "Agency", "IDR", "Asia/Jakarta", "id-ID", 1, false);
        ownerA = new LedgerPrincipal(userA.getId(), userA.getEmail(), userA.getFullName(), false, UUID.randomUUID(), orgA.getId(), OrgRole.OWNER, false);
        ownerB = new LedgerPrincipal(userB.getId(), userB.getEmail(), userB.getFullName(), false, UUID.randomUUID(), orgB.getId(), OrgRole.OWNER, false);
    }

    @Test
    void balancedJournalPostsAndDerivesNormalBalances() {
        Account bank = accountService.requireBySystemCode(orgA.getId(), "BANK");
        Account equity = accountService.requireBySystemCode(orgA.getId(), "OWNER_CAPITAL");

        var je = journalService.createManualJournal(ownerA, new JournalService.CreateJournalRequest(
                LocalDate.now(), "Owner capital injection", "CAP-01", "IDR", null, true, false,
                List.of(
                        JournalService.JournalLineInput.debit(bank.getId(), null, "Cash in bank", new BigDecimal("50000000")),
                        JournalService.JournalLineInput.credit(equity.getId(), null, "Capital contribution", new BigDecimal("50000000"))
                )
        ));

        assertThat(je.status()).isEqualTo(JournalStatus.POSTED);
        assertThat(je.totalDebit()).isEqualTo("50000000.00");
        assertThat(je.totalCredit()).isEqualTo("50000000.00");

        var accounts = accountService.listAccounts(orgA.getId(), LocalDate.now(), false);
        var bankDto = accounts.stream().filter(a -> a.id().equals(bank.getId())).findFirst().orElseThrow();
        var eqDto = accounts.stream().filter(a -> a.id().equals(equity.getId())).findFirst().orElseThrow();
        assertThat(bankDto.balance()).isEqualTo("50000000.00");
        assertThat(eqDto.balance()).isEqualTo("50000000.00");
    }

    @Test
    void unbalancedJournalIsRejectedAndRolledBack() {
        Account bank = accountService.requireBySystemCode(orgA.getId(), "BANK");
        Account equity = accountService.requireBySystemCode(orgA.getId(), "OWNER_CAPITAL");
        long beforeCount = journalEntryRepository.count();

        assertThatThrownBy(() -> journalService.createManualJournal(ownerA, new JournalService.CreateJournalRequest(
                LocalDate.now(), "Unbalanced test", "ERR-01", "IDR", null, true, false,
                List.of(
                        JournalService.JournalLineInput.debit(bank.getId(), null, "Debit", new BigDecimal("1000000")),
                        JournalService.JournalLineInput.credit(equity.getId(), null, "Credit", new BigDecimal("900000"))
                )
        ))).isInstanceOf(LedgerException.class)
                .hasMessageContaining("does not balance");

        assertThat(journalEntryRepository.count()).isEqualTo(beforeCount);
    }

    @Test
    void nonPostingHeaderAccountAndCrossOrgAccountRejected() {
        Account headerAssets = accountService.requireBySystemCode(orgA.getId(), "HEADER_ASSETS");
        Account equityA = accountService.requireBySystemCode(orgA.getId(), "OWNER_CAPITAL");
        Account bankB = accountService.requireBySystemCode(orgB.getId(), "BANK");

        assertThatThrownBy(() -> journalService.createManualJournal(ownerA, new JournalService.CreateJournalRequest(
                LocalDate.now(), "Post to header", "HDR-01", "IDR", null, true, false,
                List.of(
                        JournalService.JournalLineInput.debit(headerAssets.getId(), null, "Invalid header", new BigDecimal("100000")),
                        JournalService.JournalLineInput.credit(equityA.getId(), null, "Equity", new BigDecimal("100000"))
                )
        ))).isInstanceOf(LedgerException.class)
                .hasMessageContaining("header account");

        assertThatThrownBy(() -> journalService.createManualJournal(ownerA, new JournalService.CreateJournalRequest(
                LocalDate.now(), "Cross org account", "XORG-01", "IDR", null, true, false,
                List.of(
                        JournalService.JournalLineInput.debit(bankB.getId(), null, "Org B bank", new BigDecimal("100000")),
                        JournalService.JournalLineInput.credit(equityA.getId(), null, "Org A equity", new BigDecimal("100000"))
                )
        ))).isInstanceOf(LedgerException.class)
                .hasMessageContaining("not found in organization");
    }

    @Test
    void postedJournalIsImmutableAndReversalZerosEffect() {
        Account bank = accountService.requireBySystemCode(orgA.getId(), "BANK");
        Account revenue = accountService.requireBySystemCode(orgA.getId(), "SERVICE_REVENUE");

        var posted = journalService.createManualJournal(ownerA, new JournalService.CreateJournalRequest(
                LocalDate.now(), "Initial posting", "REV-01", "IDR", null, true, false,
                List.of(
                        JournalService.JournalLineInput.debit(bank.getId(), null, "Cash", new BigDecimal("2500000")),
                        JournalService.JournalLineInput.credit(revenue.getId(), null, "Revenue", new BigDecimal("2500000"))
                )
        ));

        assertThatThrownBy(() -> journalService.updateDraftJournal(ownerA, posted.id(),
                new JournalService.UpdateJournalRequest(null, "Tampered", null, null)))
                .isInstanceOf(LedgerException.class)
                .hasMessageContaining("immutable");

        assertThatThrownBy(() -> journalService.deleteDraftJournal(ownerA, posted.id()))
                .isInstanceOf(LedgerException.class);

        var reversal = journalService.reverseJournal(ownerA, posted.id(), LocalDate.now(), "Entered in error");
        assertThat(reversal.reversedEntryId()).isEqualTo(posted.id());
        assertThat(journalService.getById(orgA.getId(), posted.id()).status()).isEqualTo(JournalStatus.REVERSED);

        var bankBal = accountService.listAccounts(orgA.getId(), LocalDate.now(), false).stream()
                .filter(a -> a.id().equals(bank.getId())).findFirst().orElseThrow().balance();
        assertThat(bankBal).isEqualTo("0.00");
    }

    @Test
    void closedPeriodRejectsPostingsUntilReopened() {
        LocalDate targetDate = LocalDate.now().withDayOfMonth(15);
        var period = periodService.listPeriods(orgA.getId()).stream()
                .filter(p -> !targetDate.isBefore(p.startDate()) && !targetDate.isAfter(p.endDate()))
                .findFirst().orElseThrow();

        periodService.closePeriod(ownerA, period.id());

        Account bank = accountService.requireBySystemCode(orgA.getId(), "BANK");
        Account equity = accountService.requireBySystemCode(orgA.getId(), "OWNER_CAPITAL");

        assertThatThrownBy(() -> journalService.createManualJournal(ownerA, new JournalService.CreateJournalRequest(
                targetDate, "Post into closed period", "CL-01", "IDR", null, true, false,
                List.of(
                        JournalService.JournalLineInput.debit(bank.getId(), null, "Bank", new BigDecimal("100000")),
                        JournalService.JournalLineInput.credit(equity.getId(), null, "Equity", new BigDecimal("100000"))
                )
        ))).isInstanceOf(LedgerException.class)
                .hasMessageContaining("closed");

        periodService.reopenPeriod(ownerA, period.id(), "Audit adjustment required");
        var posted = journalService.createManualJournal(ownerA, new JournalService.CreateJournalRequest(
                targetDate, "Post after reopen", "CL-02", "IDR", null, true, false,
                List.of(
                        JournalService.JournalLineInput.debit(bank.getId(), null, "Bank", new BigDecimal("100000")),
                        JournalService.JournalLineInput.credit(equity.getId(), null, "Equity", new BigDecimal("100000"))
                )
        ));
        assertThat(posted.status()).isEqualTo(JournalStatus.POSTED);
    }

    @Test
    void duplicateBusinessEventKeyIsRejectedAndPostedAccountCannotBeDeleted() {
        Account bank = accountService.requireBySystemCode(orgA.getId(), "BANK");
        Account customRev = accountService.requireAccount(orgA.getId(),
                accountService.createAccount(ownerA, new AccountService.CreateAccountRequest(
                        "4090", "Special Advisory Revenue", AccountType.REVENUE, NormalBalance.CREDIT,
                        null, CashFlowCategory.OPERATING, null, "IDR", true
                )).id());

        String eventKey = "UNIQUE_EVENT:" + UUID.randomUUID();
        journalService.postAutomatedJournal(
                orgA.getId(), ownerA.userId(), LocalDate.now(), "Event posting", "EV-1",
                JournalSourceType.MANUAL, null, eventKey, "IDR", null,
                List.of(
                        JournalService.JournalLineInput.debit(bank.getId(), null, "Bank", new BigDecimal("500000")),
                        JournalService.JournalLineInput.credit(customRev.getId(), null, "Rev", new BigDecimal("500000"))
                )
        );

        assertThatThrownBy(() -> journalService.postAutomatedJournal(
                orgA.getId(), ownerA.userId(), LocalDate.now(), "Duplicate event", "EV-1",
                JournalSourceType.MANUAL, null, eventKey, "IDR", null,
                List.of(
                        JournalService.JournalLineInput.debit(bank.getId(), null, "Bank", new BigDecimal("500000")),
                        JournalService.JournalLineInput.credit(customRev.getId(), null, "Rev", new BigDecimal("500000"))
                )
        )).isInstanceOf(LedgerException.class)
                .hasMessageContaining("already posted");

        assertThatThrownBy(() -> accountService.deleteAccount(ownerA, customRev.getId()))
                .isInstanceOf(LedgerException.class)
                .hasMessageContaining("cannot be deleted");
    }
}
