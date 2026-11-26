package com.ledger.reporting;

import com.ledger.audit.AuditService;
import com.ledger.journal.GeneralLedgerService;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportExportService {

    private static final Color FOREST = new Color(24, 61, 48);
    private static final Color BORDER = new Color(214, 222, 217);
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 15, Font.BOLD, FOREST);
    private static final Font SUB_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(92, 102, 96));
    private static final Font HEAD_FONT = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, new Color(34, 34, 34));
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 8.5f, Font.BOLD, new Color(34, 34, 34));

    private final AuditService auditService;

    public ReportExportService(AuditService auditService) {
        this.auditService = auditService;
    }

    public String trialBalanceCsv(UUID orgId, ReportingService.TrialBalanceReport report) {
        StringWriter sw = new StringWriter();
        try (CSVPrinter p = new CSVPrinter(sw, CSVFormat.DEFAULT.builder()
                .setHeader("Account Code", "Account Name", "Type", "Debit", "Credit").build())) {
            for (ReportingService.TrialBalanceRow r : report.rows()) {
                p.printRecord(r.accountCode(), r.accountName(), r.accountType(), r.debit(), r.credit());
            }
            p.printRecord("", "TOTAL", "", report.totalDebit(), report.totalCredit());
        } catch (IOException ex) {
            throw new IllegalStateException("CSV export failed", ex);
        }
        auditExport(orgId, "TRIAL_BALANCE", "CSV");
        return sw.toString();
    }

    public String generalLedgerCsv(UUID orgId, GeneralLedgerService.GeneralLedgerResponse gl) {
        StringWriter sw = new StringWriter();
        try (CSVPrinter p = new CSVPrinter(sw, CSVFormat.DEFAULT.builder()
                .setHeader("Date", "Entry Number", "Description", "Reference", "Source", "Debit", "Credit", "Running Balance").build())) {
            p.printRecord("", "OPENING", "Opening Balance", "", "", "0.00", "0.00", gl.openingBalance());
            for (GeneralLedgerService.LedgerEntryRow r : gl.rows()) {
                p.printRecord(r.entryDate(), r.entryNumber(), r.entryDescription(), r.reference(), r.sourceType(),
                        r.debit(), r.credit(), r.runningBalance());
            }
            p.printRecord("", "CLOSING", "Closing Balance", "", "", gl.periodDebitTotal(), gl.periodCreditTotal(), gl.closingBalance());
        } catch (IOException ex) {
            throw new IllegalStateException("CSV export failed", ex);
        }
        auditExport(orgId, "GENERAL_LEDGER", "CSV");
        return sw.toString();
    }

    public String profitAndLossCsv(UUID orgId, ReportingService.ProfitAndLossReport report) {
        StringWriter sw = new StringWriter();
        try (CSVPrinter p = new CSVPrinter(sw, CSVFormat.DEFAULT.builder()
                .setHeader("Section", "Account Code", "Account Name", "Amount (" + report.currencyCode() + ")").build())) {
            for (ReportingService.StatementLine l : report.revenueLines()) {
                p.printRecord("REVENUE", l.accountCode(), l.accountName(), l.amount());
            }
            p.printRecord("REVENUE", "", "Total Revenue", report.totalRevenue());
            for (ReportingService.StatementLine l : report.expenseLines()) {
                p.printRecord("EXPENSE", l.accountCode(), l.accountName(), l.amount());
            }
            p.printRecord("EXPENSE", "", "Total Expenses", report.totalExpenses());
            p.printRecord("SUMMARY", "", "Net Profit / (Loss)", report.netProfit());
        } catch (IOException ex) {
            throw new IllegalStateException("CSV export failed", ex);
        }
        auditExport(orgId, "PROFIT_AND_LOSS", "CSV");
        return sw.toString();
    }

    public String balanceSheetCsv(UUID orgId, ReportingService.BalanceSheetReport report) {
        StringWriter sw = new StringWriter();
        try (CSVPrinter p = new CSVPrinter(sw, CSVFormat.DEFAULT.builder()
                .setHeader("Section", "Account Code", "Account Name", "Balance (" + report.currencyCode() + ")").build())) {
            for (ReportingService.StatementLine l : report.assets()) p.printRecord("ASSET", l.accountCode(), l.accountName(), l.amount());
            p.printRecord("ASSET", "", "Total Assets", report.totalAssets());
            for (ReportingService.StatementLine l : report.liabilities()) p.printRecord("LIABILITY", l.accountCode(), l.accountName(), l.amount());
            p.printRecord("LIABILITY", "", "Total Liabilities", report.totalLiabilities());
            for (ReportingService.StatementLine l : report.equity()) p.printRecord("EQUITY", l.accountCode(), l.accountName(), l.amount());
            p.printRecord("EQUITY", "", "Total Equity", report.totalEquity());
            p.printRecord("SUMMARY", "", "Total Liabilities + Equity", report.totalLiabilitiesAndEquity());
        } catch (IOException ex) {
            throw new IllegalStateException("CSV export failed", ex);
        }
        auditExport(orgId, "BALANCE_SHEET", "CSV");
        return sw.toString();
    }

    public byte[] renderSimpleReportPdf(UUID orgId, String reportKey, String title, String subtitle,
                                        List<String> headers, List<List<String>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 40, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();

        doc.add(new Paragraph(title, TITLE_FONT));
        Paragraph sub = new Paragraph(subtitle, SUB_FONT);
        sub.setSpacingAfter(14f);
        doc.add(sub);

        PdfPTable table = new PdfPTable(headers.size());
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, HEAD_FONT));
            cell.setBackgroundColor(FOREST);
            cell.setBorderColor(FOREST);
            cell.setPadding(6f);
            table.addCell(cell);
        }

        for (int rIdx = 0; rIdx < rows.size(); rIdx++) {
            List<String> row = rows.get(rIdx);
            boolean isSummary = rIdx == rows.size() - 1;
            for (int cIdx = 0; cIdx < row.size(); cIdx++) {
                String val = row.get(cIdx) != null ? row.get(cIdx) : "";
                PdfPCell cell = new PdfPCell(new Phrase(val, isSummary ? BOLD_FONT : CELL_FONT));
                cell.setBorderColor(BORDER);
                cell.setPadding(5.5f);
                if (cIdx >= headers.size() - 2 && headers.size() >= 3) {
                    cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                }
                table.addCell(cell);
            }
        }
        doc.add(table);
        doc.close();

        auditExport(orgId, reportKey, "PDF");
        return out.toByteArray();
    }

    private void auditExport(UUID orgId, String reportType, String format) {
        auditService.record(orgId, "DOCUMENT_EXPORTED", "REPORT", reportType,
                "Exported " + reportType + " as " + format, Map.of("format", format));
    }
}
