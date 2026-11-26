package com.ledger.banking;

import com.ledger.accounting.Account;
import com.ledger.accounting.AccountService;
import com.ledger.accounting.AccountType;
import com.ledger.audit.AuditService;
import com.ledger.identity.SessionAuthenticationFilter;
import com.ledger.journal.JournalEntry;
import com.ledger.journal.JournalLine;
import com.ledger.journal.JournalLineRepository;
import com.ledger.journal.JournalService;
import com.ledger.journal.JournalSourceType;
import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationService;
import com.ledger.shared.LedgerException;
import com.ledger.shared.LedgerPrincipal;
import com.ledger.shared.MoneyUtil;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class BankingService {

    private final BankAccountRepository bankAccountRepository;
    private final BankStatementImportRepository importRepository;
    private final BankStatementLineRepository lineRepository;
    private final AccountService accountService;
    private final OrganizationService organizationService;
    private final JournalService journalService;
    private final JournalLineRepository journalLineRepository;
    private final AuditService auditService;

    public BankingService(
            BankAccountRepository bankAccountRepository,
            BankStatementImportRepository importRepository,
            BankStatementLineRepository lineRepository,
            AccountService accountService,
            OrganizationService organizationService,
            JournalService journalService,
            JournalLineRepository journalLineRepository,
            AuditService auditService) {
        this.bankAccountRepository = bankAccountRepository;
        this.importRepository = importRepository;
        this.lineRepository = lineRepository;
        this.accountService = accountService;
        this.organizationService = organizationService;
        this.journalService = journalService;
        this.journalLineRepository = journalLineRepository;
        this.auditService = auditService;
    }

    @Transactional
    public BankAccountDto createBankAccount(LedgerPrincipal principal, CreateBankAccountRequest req) {
        principal.requireWriteAccounting();
        UUID orgId = principal.requireOrganizationId();
        Organization org = organizationService.getOrganizationEntity(orgId);

        Account glAccount = accountService.requirePostingAccount(orgId, req.glAccountId());
        if (glAccount.getAccountType() != AccountType.ASSET) {
            throw LedgerException.businessRule("INVALID_GL_ACCOUNT", "Bank account must link to an Asset posting account");
        }
        if (bankAccountRepository.existsByOrganizationIdAndGlAccountId(orgId, glAccount.getId())) {
            throw LedgerException.conflict("GL_ACCOUNT_LINKED", "This accounting account is already linked to another bank account");
        }

        String ccy = (req.currencyCode() == null || req.currencyCode().isBlank())
                ? org.getBaseCurrency() : req.currencyCode().toUpperCase(Locale.ROOT);
        BigDecimal opening = req.openingBalance() != null ? MoneyUtil.round(req.openingBalance(), ccy) : MoneyUtil.ZERO;
        LocalDate openingDate = req.openingBalanceDate() != null ? req.openingBalanceDate() : LocalDate.now();

        UUID openingJournalId = null;
        if (MoneyUtil.isPositive(opening)) {
            Account equity = accountService.requireBySystemCode(orgId, "OWNER_CAPITAL");
            JournalEntry je = journalService.postAutomatedJournal(
                    orgId, principal.userId(), openingDate,
                    "Opening balance for bank account " + req.name().trim(),
                    "OPEN-" + glAccount.getCode(), JournalSourceType.OPENING_BALANCE, glAccount.getId(),
                    "BANK_OPEN:" + glAccount.getId(), ccy, null,
                    List.of(
                            JournalService.JournalLineInput.debit(glAccount.getId(), null, "Opening balance", opening),
                            JournalService.JournalLineInput.credit(equity.getId(), null, "Opening balance equity", opening)
                    )
            );
            openingJournalId = je.getId();
        }

        BankAccount bankAccount = new BankAccount(
                orgId, req.name(), req.institutionName(), req.maskedAccountNumber(),
                ccy, glAccount.getId(), opening, openingDate, openingJournalId
        );
        bankAccountRepository.save(bankAccount);

        auditService.record(orgId, "BANK_ACCOUNT_CREATED", "BANK_ACCOUNT", bankAccount.getId().toString(),
                "Created bank account " + bankAccount.getName(), Map.of("glAccount", glAccount.getCode()));
        return toDto(bankAccount);
    }

    @Transactional(readOnly = true)
    public List<BankAccountDto> listBankAccounts(UUID organizationId) {
        return bankAccountRepository.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public BankAccountDetailDto getBankAccountDetail(UUID organizationId, UUID bankAccountId) {
        BankAccount ba = requireBankAccount(organizationId, bankAccountId);
        List<BankStatementImportDto> imports = importRepository
                .findByOrganizationIdAndBankAccountIdOrderByImportedAtDesc(organizationId, bankAccountId)
                .stream()
                .map(i -> new BankStatementImportDto(i.getId(), i.getFileName(), i.getTotalRows(), i.getImportedRows(), i.getDuplicateRows(), i.getImportedAt()))
                .toList();
        List<BankStatementLineDto> lines = lineRepository
                .findByOrganizationIdAndBankAccountIdOrderByTransactionDateDescCreatedAtDesc(organizationId, bankAccountId)
                .stream()
                .map(BankStatementLineDto::from)
                .toList();
        return new BankAccountDetailDto(toDto(ba), imports, lines);
    }

    @Transactional(readOnly = true)
    public CsvImportPreview previewCsvImport(UUID organizationId, UUID bankAccountId, CsvImportRequest req) {
        BankAccount ba = requireBankAccount(organizationId, bankAccountId);
        return parseCsv(organizationId, ba, req);
    }

    @Transactional
    public CsvImportCommitResult commitCsvImport(LedgerPrincipal principal, UUID bankAccountId, CsvImportRequest req) {
        principal.requireFinanceOperations();
        UUID orgId = principal.requireOrganizationId();
        BankAccount ba = requireBankAccount(orgId, bankAccountId);

        CsvImportPreview preview = parseCsv(orgId, ba, req);
        if (!preview.errors().isEmpty()) {
            throw LedgerException.badRequest(
                    "CSV_VALIDATION_FAILED",
                    "CSV contains " + preview.errors().size() + " validation error(s): " + preview.errors().getFirst().message()
            );
        }

        String fileHash = SessionAuthenticationFilter.sha256Hex(req.csvContent());
        String fileName = (req.fileName() == null || req.fileName().isBlank()) ? "statement.csv" : req.fileName().trim();

        int newRows = (int) preview.rows().stream().filter(r -> !r.duplicate()).count();
        int dupRows = (int) preview.rows().stream().filter(ParsedCsvRow::duplicate).count();

        BankStatementImport imp = new BankStatementImport(
                orgId, ba.getId(), fileName, fileHash, preview.rows().size(), newRows, dupRows, principal.userId()
        );
        importRepository.save(imp);

        List<BankStatementLineDto> created = new ArrayList<>();
        for (ParsedCsvRow row : preview.rows()) {
            if (row.duplicate()) {
                continue;
            }
            BankStatementLine line = new BankStatementLine(
                    orgId, ba.getId(), imp.getId(), row.transactionDate(),
                    row.description(), row.reference(), new BigDecimal(row.amount()), row.fingerprint()
            );
            lineRepository.save(line);
            created.add(BankStatementLineDto.from(line));
        }

        auditService.record(orgId, "BANK_STATEMENT_IMPORTED", "BANK_ACCOUNT", ba.getId().toString(),
                "Imported " + newRows + " statement lines (" + dupRows + " duplicates skipped) into " + ba.getName(),
                Map.of("importId", imp.getId().toString(), "fileName", fileName));

        return new CsvImportCommitResult(imp.getId(), fileName, preview.rows().size(), newRows, dupRows, created);
    }

    @Transactional(readOnly = true)
    public BankAccount requireBankAccount(UUID organizationId, UUID bankAccountId) {
        return bankAccountRepository.findByIdAndOrganizationId(bankAccountId, organizationId)
                .orElseThrow(() -> LedgerException.notFound("Bank account not found"));
    }

    private CsvImportPreview parseCsv(UUID orgId, BankAccount ba, CsvImportRequest req) {
        if (req.csvContent() == null || req.csvContent().isBlank()) {
            throw LedgerException.badRequest("EMPTY_CSV", "CSV content cannot be empty");
        }
        String dateCol = defaultIfBlank(req.dateColumn(), "Date");
        String descCol = defaultIfBlank(req.descriptionColumn(), "Description");
        String refCol = defaultIfBlank(req.referenceColumn(), "Reference");
        String debitCol = req.debitColumn();
        String creditCol = req.creditColumn();
        String amountCol = req.amountColumn();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern(defaultIfBlank(req.dateFormat(), "yyyy-MM-dd"));

        List<ParsedCsvRow> rows = new ArrayList<>();
        List<CsvRowError> errors = new ArrayList<>();
        Map<String, Integer> occurrenceCounter = new HashMap<>();

        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build()
                .parse(new StringReader(req.csvContent()))) {

            List<String> headers = parser.getHeaderNames();
            int rowNum = 1;
            for (CSVRecord record : parser) {
                rowNum++;
                String rawDate = getOptional(record, dateCol);
                String desc = getOptional(record, descCol);
                String ref = getOptional(record, refCol);

                LocalDate txDate;
                try {
                    txDate = LocalDate.parse(rawDate, dateFmt);
                } catch (DateTimeParseException | NullPointerException e) {
                    errors.add(new CsvRowError(rowNum, "Invalid date value: '" + rawDate + "'"));
                    continue;
                }
                if (desc == null || desc.isBlank()) {
                    errors.add(new CsvRowError(rowNum, "Description is required"));
                    continue;
                }

                BigDecimal signedAmount;
                try {
                    if (amountCol != null && !amountCol.isBlank() && record.isMapped(amountCol)) {
                        signedAmount = parseDecimal(getOptional(record, amountCol));
                    } else {
                        String dCol = defaultIfBlank(debitCol, "Debit");
                        String cCol = defaultIfBlank(creditCol, "Credit");
                        BigDecimal debitOutflow = parseOptionalDecimal(getOptional(record, dCol));
                        BigDecimal creditInflow = parseOptionalDecimal(getOptional(record, cCol));
                        // On bank statements, Credit is inflow (+) and Debit is outflow (-).
                        signedAmount = creditInflow.subtract(debitOutflow);
                    }
                    signedAmount = MoneyUtil.round(signedAmount, ba.getCurrencyCode());
                    if (MoneyUtil.isZero(signedAmount)) {
                        errors.add(new CsvRowError(rowNum, "Amount cannot be zero"));
                        continue;
                    }
                } catch (NumberFormatException ex) {
                    errors.add(new CsvRowError(rowNum, "Invalid numeric amount"));
                    continue;
                }

                String baseKey = ba.getId() + "|" + txDate + "|" + MoneyUtil.toMoneyString(signedAmount)
                        + "|" + (ref != null ? ref.trim() : "") + "|" + desc.trim();
                int occurrence = occurrenceCounter.merge(baseKey, 1, Integer::sum);
                String fingerprint = SessionAuthenticationFilter.sha256Hex(baseKey + "|#" + occurrence);
                boolean duplicate = lineRepository.existsByOrganizationIdAndBankAccountIdAndFingerprint(orgId, ba.getId(), fingerprint);

                rows.add(new ParsedCsvRow(rowNum, txDate, desc.trim(), ref != null ? ref.trim() : "",
                        MoneyUtil.toMoneyString(signedAmount), fingerprint, duplicate));
            }
            return new CsvImportPreview(headers, rows, errors);
        } catch (IOException ex) {
            throw LedgerException.badRequest("CSV_PARSE_ERROR", "Unable to parse CSV content: " + ex.getMessage());
        }
    }

    private BankAccountDto toDto(BankAccount ba) {
        Account gl = accountService.requireAccount(ba.getOrganizationId(), ba.getGlAccountId());
        BigDecimal ledgerBalance = MoneyUtil.ZERO;
        for (JournalLine jl : journalLineRepository.findActivePostedLinesForAccount(ba.getOrganizationId(), ba.getGlAccountId())) {
            ledgerBalance = ledgerBalance.add(gl.getNormalBalance().netChange(jl.getDebitAmount(), jl.getCreditAmount()));
        }

        List<BankStatementLine> stmtLines = lineRepository
                .findByOrganizationIdAndBankAccountIdOrderByTransactionDateDescCreatedAtDesc(ba.getOrganizationId(), ba.getId());
        BigDecimal stmtMovement = MoneyUtil.ZERO;
        int unmatchedCount = 0;
        for (BankStatementLine l : stmtLines) {
            stmtMovement = stmtMovement.add(l.getAmount());
            if (!l.isReconciled()) unmatchedCount++;
        }
        BigDecimal statementBalance = ba.getOpeningBalance().add(stmtMovement);
        BigDecimal difference = ledgerBalance.subtract(statementBalance);

        return new BankAccountDto(
                ba.getId(), ba.getName(), ba.getInstitutionName(), ba.getMaskedAccountNumber(),
                ba.getCurrencyCode(), ba.getGlAccountId(), gl.getCode(), gl.getName(),
                MoneyUtil.toMoneyString(ba.getOpeningBalance()), ba.getOpeningBalanceDate(),
                MoneyUtil.toMoneyString(ledgerBalance),
                MoneyUtil.toMoneyString(statementBalance),
                MoneyUtil.toMoneyString(difference),
                stmtLines.size(), unmatchedCount, ba.isActive()
        );
    }

    private String getOptional(CSVRecord rec, String col) {
        if (col == null || col.isBlank() || !rec.isMapped(col)) return "";
        return rec.get(col) != null ? rec.get(col).trim() : "";
    }

    private BigDecimal parseDecimal(String raw) {
        String cleaned = raw.replaceAll("[,\\s]", "");
        return new BigDecimal(cleaned);
    }

    private BigDecimal parseOptionalDecimal(String raw) {
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
        return parseDecimal(raw).abs();
    }

    private String defaultIfBlank(String val, String fallback) {
        return (val == null || val.isBlank()) ? fallback : val.trim();
    }

    public record CreateBankAccountRequest(
            @jakarta.validation.constraints.NotBlank String name,
            @jakarta.validation.constraints.NotBlank String institutionName,
            String maskedAccountNumber,
            String currencyCode,
            @jakarta.validation.constraints.NotNull UUID glAccountId,
            BigDecimal openingBalance,
            LocalDate openingBalanceDate
    ) {}

    public record CsvImportRequest(
            String fileName,
            @jakarta.validation.constraints.NotBlank String csvContent,
            String dateColumn,
            String descriptionColumn,
            String referenceColumn,
            String debitColumn,
            String creditColumn,
            String amountColumn,
            String dateFormat
    ) {}

    public record ParsedCsvRow(
            int rowNumber,
            LocalDate transactionDate,
            String description,
            String reference,
            String amount,
            String fingerprint,
            boolean duplicate
    ) {}

    public record CsvRowError(int rowNumber, String message) {}

    public record CsvImportPreview(
            List<String> headers,
            List<ParsedCsvRow> rows,
            List<CsvRowError> errors
    ) {}

    public record CsvImportCommitResult(
            UUID importId,
            String fileName,
            int totalRows,
            int importedRows,
            int duplicateRows,
            List<BankStatementLineDto> lines
    ) {}

    public record BankStatementImportDto(
            UUID id,
            String fileName,
            int totalRows,
            int importedRows,
            int duplicateRows,
            Instant importedAt
    ) {}

    public record BankStatementLineDto(
            UUID id,
            UUID statementImportId,
            LocalDate transactionDate,
            String description,
            String reference,
            String amount,
            boolean reconciled
    ) {
        public static BankStatementLineDto from(BankStatementLine l) {
            return new BankStatementLineDto(
                    l.getId(), l.getStatementImportId(), l.getTransactionDate(),
                    l.getDescription(), l.getReference(), MoneyUtil.toMoneyString(l.getAmount()), l.isReconciled()
            );
        }
    }

    public record BankAccountDto(
            UUID id,
            String name,
            String institutionName,
            String maskedAccountNumber,
            String currencyCode,
            UUID glAccountId,
            String glAccountCode,
            String glAccountName,
            String openingBalance,
            LocalDate openingBalanceDate,
            String ledgerBalance,
            String statementBalance,
            String reconciliationDifference,
            int totalStatementLines,
            int unmatchedLineCount,
            boolean active
    ) {}

    public record BankAccountDetailDto(
            BankAccountDto bankAccount,
            List<BankStatementImportDto> imports,
            List<BankStatementLineDto> statementLines
    ) {}
}
