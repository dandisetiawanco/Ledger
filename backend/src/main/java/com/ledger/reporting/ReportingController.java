package com.ledger.reporting;

import com.ledger.journal.GeneralLedgerService;
import com.ledger.shared.LedgerPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private final ReportingService reportingService;
    private final GeneralLedgerService generalLedgerService;
    private final ReportExportService exportService;

    public ReportingController(
            ReportingService reportingService,
            GeneralLedgerService generalLedgerService,
            ReportExportService exportService) {
        this.reportingService = reportingService;
        this.generalLedgerService = generalLedgerService;
        this.exportService = exportService;
    }

    @GetMapping("/overview")
    public ReportingService.OverviewDto overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return reportingService.overview(LedgerPrincipal.current().requireOrganizationId(), fromDate, toDate);
    }

    @GetMapping("/trial-balance")
    public ReportingService.TrialBalanceReport trialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        return reportingService.trialBalance(LedgerPrincipal.current().requireOrganizationId(), asOfDate);
    }

    @GetMapping("/profit-loss")
    public ReportingService.ProfitAndLossReport profitAndLoss(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate compareFromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate compareToDate
    ) {
        return reportingService.profitAndLoss(
                LedgerPrincipal.current().requireOrganizationId(), fromDate, toDate, compareFromDate, compareToDate
        );
    }

    @GetMapping("/balance-sheet")
    public ReportingService.BalanceSheetReport balanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate compareAsOfDate
    ) {
        return reportingService.balanceSheet(LedgerPrincipal.current().requireOrganizationId(), asOfDate, compareAsOfDate);
    }

    @GetMapping("/cash-flow")
    public ReportingService.CashFlowReport cashFlow(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return reportingService.cashFlow(LedgerPrincipal.current().requireOrganizationId(), fromDate, toDate);
    }

    @GetMapping("/receivables-aging")
    public ReportingService.AgingReport receivablesAging(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        return reportingService.receivablesAging(LedgerPrincipal.current().requireOrganizationId(), asOfDate);
    }

    @GetMapping("/payables-aging")
    public ReportingService.AgingReport payablesAging(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        return reportingService.payablesAging(LedgerPrincipal.current().requireOrganizationId(), asOfDate);
    }

    @GetMapping("/trial-balance/export")
    public ResponseEntity<byte[]> exportTrialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(defaultValue = "csv") String format
    ) {
        UUID orgId = LedgerPrincipal.current().requireOrganizationId();
        ReportingService.TrialBalanceReport report = reportingService.trialBalance(orgId, asOfDate);
        if ("pdf".equalsIgnoreCase(format)) {
            List<List<String>> rows = new ArrayList<>();
            for (ReportingService.TrialBalanceRow r : report.rows()) {
                rows.add(List.of(r.accountCode(), r.accountName(), r.accountType(), r.debit(), r.credit()));
            }
            rows.add(List.of("", "TOTAL", "", report.totalDebit(), report.totalCredit()));
            byte[] pdf = exportService.renderSimpleReportPdf(
                    orgId, "TRIAL_BALANCE",
                    report.organizationName() + " - Trial Balance",
                    "As of " + report.asOfDate() + " | Currency: " + report.currencyCode(),
                    List.of("Code", "Account", "Type", "Debit", "Credit"), rows
            );
            return pdfResponse("trial-balance-" + report.asOfDate() + ".pdf", pdf);
        }
        return csvResponse("trial-balance-" + report.asOfDate() + ".csv", exportService.trialBalanceCsv(orgId, report));
    }

    @GetMapping("/profit-loss/export")
    public ResponseEntity<byte[]> exportProfitLoss(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "csv") String format
    ) {
        UUID orgId = LedgerPrincipal.current().requireOrganizationId();
        ReportingService.ProfitAndLossReport report = reportingService.profitAndLoss(orgId, fromDate, toDate, null, null);
        if ("pdf".equalsIgnoreCase(format)) {
            List<List<String>> rows = new ArrayList<>();
            for (ReportingService.StatementLine l : report.revenueLines()) {
                rows.add(List.of("Revenue", l.accountCode(), l.accountName(), l.amount()));
            }
            rows.add(List.of("", "", "Total Revenue", report.totalRevenue()));
            for (ReportingService.StatementLine l : report.expenseLines()) {
                rows.add(List.of("Expense", l.accountCode(), l.accountName(), l.amount()));
            }
            rows.add(List.of("", "", "Total Expenses", report.totalExpenses()));
            rows.add(List.of("NET", "", "Net Profit / (Loss)", report.netProfit()));
            byte[] pdf = exportService.renderSimpleReportPdf(
                    orgId, "PROFIT_AND_LOSS",
                    report.organizationName() + " - Profit and Loss Statement",
                    report.fromDate() + " to " + report.toDate() + " | Currency: " + report.currencyCode(),
                    List.of("Section", "Code", "Account", "Amount"), rows
            );
            return pdfResponse("profit-and-loss-" + report.fromDate() + "-to-" + report.toDate() + ".pdf", pdf);
        }
        return csvResponse("profit-and-loss.csv", exportService.profitAndLossCsv(orgId, report));
    }

    @GetMapping("/balance-sheet/export")
    public ResponseEntity<byte[]> exportBalanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(defaultValue = "csv") String format
    ) {
        UUID orgId = LedgerPrincipal.current().requireOrganizationId();
        ReportingService.BalanceSheetReport report = reportingService.balanceSheet(orgId, asOfDate, null);
        if ("pdf".equalsIgnoreCase(format)) {
            List<List<String>> rows = new ArrayList<>();
            for (ReportingService.StatementLine l : report.assets()) rows.add(List.of("Asset", l.accountCode(), l.accountName(), l.amount()));
            rows.add(List.of("", "", "Total Assets", report.totalAssets()));
            for (ReportingService.StatementLine l : report.liabilities()) rows.add(List.of("Liability", l.accountCode(), l.accountName(), l.amount()));
            rows.add(List.of("", "", "Total Liabilities", report.totalLiabilities()));
            for (ReportingService.StatementLine l : report.equity()) rows.add(List.of("Equity", l.accountCode(), l.accountName(), l.amount()));
            rows.add(List.of("", "", "Total Equity", report.totalEquity()));
            rows.add(List.of("CHECK", "", "Total Liabilities & Equity", report.totalLiabilitiesAndEquity()));
            byte[] pdf = exportService.renderSimpleReportPdf(
                    orgId, "BALANCE_SHEET",
                    report.organizationName() + " - Balance Sheet",
                    "As of " + report.asOfDate() + " | Currency: " + report.currencyCode() + " | Balanced: " + report.balanced(),
                    List.of("Section", "Code", "Account", "Balance"), rows
            );
            return pdfResponse("balance-sheet-" + report.asOfDate() + ".pdf", pdf);
        }
        return csvResponse("balance-sheet-" + report.asOfDate() + ".csv", exportService.balanceSheetCsv(orgId, report));
    }

    @GetMapping("/general-ledger/export")
    public ResponseEntity<byte[]> exportGeneralLedger(
            @RequestParam UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "csv") String format
    ) {
        UUID orgId = LedgerPrincipal.current().requireOrganizationId();
        GeneralLedgerService.GeneralLedgerResponse gl = generalLedgerService.getLedger(orgId, accountId, fromDate, toDate, null, null, 0, 500);
        if ("pdf".equalsIgnoreCase(format)) {
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("", "OPENING", "Opening Balance", "0.00", "0.00", gl.openingBalance()));
            for (GeneralLedgerService.LedgerEntryRow r : gl.rows()) {
                rows.add(List.of(r.entryDate().toString(), r.entryNumber(), r.entryDescription(), r.debit(), r.credit(), r.runningBalance()));
            }
            rows.add(List.of("", "CLOSING", "Closing Balance", gl.periodDebitTotal(), gl.periodCreditTotal(), gl.closingBalance()));
            byte[] pdf = exportService.renderSimpleReportPdf(
                    orgId, "GENERAL_LEDGER",
                    "General Ledger: " + gl.accountCode() + " " + gl.accountName(),
                    "Normal Balance: " + gl.normalBalance(),
                    List.of("Date", "Journal", "Description", "Debit", "Credit", "Balance"), rows
            );
            return pdfResponse("general-ledger-" + gl.accountCode() + ".pdf", pdf);
        }
        return csvResponse("general-ledger-" + gl.accountCode() + ".csv", exportService.generalLedgerCsv(orgId, gl));
    }

    private ResponseEntity<byte[]> csvResponse(String filename, String content) {
        ContentDisposition cd = ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<byte[]> pdfResponse(String filename, byte[] bytes) {
        ContentDisposition cd = ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
