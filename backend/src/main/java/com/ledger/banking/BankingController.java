package com.ledger.banking;

import com.ledger.shared.LedgerPrincipal;
import jakarta.validation.Valid;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/banking")
public class BankingController {

    private final BankingService bankingService;

    public BankingController(BankingService bankingService) {
        this.bankingService = bankingService;
    }

    @GetMapping("/accounts")
    public List<BankingService.BankAccountDto> listAccounts() {
        return bankingService.listBankAccounts(LedgerPrincipal.current().requireOrganizationId());
    }

    @GetMapping("/accounts/{bankAccountId}")
    public BankingService.BankAccountDetailDto getAccount(@PathVariable UUID bankAccountId) {
        return bankingService.getBankAccountDetail(LedgerPrincipal.current().requireOrganizationId(), bankAccountId);
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public BankingService.BankAccountDto createAccount(@Valid @RequestBody BankingService.CreateBankAccountRequest req) {
        return bankingService.createBankAccount(LedgerPrincipal.current(), req);
    }

    @PostMapping("/accounts/{bankAccountId}/import/preview")
    public BankingService.CsvImportPreview previewImport(
            @PathVariable UUID bankAccountId,
            @Valid @RequestBody BankingService.CsvImportRequest req
    ) {
        return bankingService.previewCsvImport(LedgerPrincipal.current().requireOrganizationId(), bankAccountId, req);
    }

    @PostMapping("/accounts/{bankAccountId}/import/commit")
    @ResponseStatus(HttpStatus.CREATED)
    public BankingService.CsvImportCommitResult commitImport(
            @PathVariable UUID bankAccountId,
            @Valid @RequestBody BankingService.CsvImportRequest req
    ) {
        return bankingService.commitCsvImport(LedgerPrincipal.current(), bankAccountId, req);
    }

    @GetMapping("/sample-statement.csv")
    public ResponseEntity<String> sampleCsv() throws IOException {
        ClassPathResource res = new ClassPathResource("sample/sample-bank-statement.csv");
        String content = res.getContentAsString(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sample-bank-statement.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(content);
    }
}
