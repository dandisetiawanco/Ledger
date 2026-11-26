package com.ledger.invoice;

import com.ledger.audit.AuditService;
import com.ledger.contact.Contact;
import com.ledger.contact.ContactService;
import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationService;
import com.ledger.payment.Payment;
import com.ledger.shared.MoneyUtil;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Generates clean A4 PDF documents for invoices, credit notes, and payment receipts
 * using OpenPDF with repeating table headers and protected totals blocks.
 */
@Service
public class InvoicePdfService {

    private static final Color FOREST = new Color(24, 61, 48);
    private static final Color SAGE_BG = new Color(244, 247, 243);
    private static final Color BORDER = new Color(214, 222, 217);
    private static final Color INK = new Color(34, 34, 34);
    private static final Color MUTED = new Color(92, 102, 96);

    private static final Font BRAND_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, FOREST);
    private static final Font DOC_TITLE_FONT = new Font(Font.HELVETICA, 20, Font.BOLD, INK);
    private static final Font LABEL_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, MUTED);
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, INK);
    private static final Font BODY_BOLD = new Font(Font.HELVETICA, 9.5f, Font.BOLD, INK);
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE);
    private static final Font TOTAL_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, FOREST);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final OrganizationService organizationService;
    private final ContactService contactService;
    private final AuditService auditService;

    public InvoicePdfService(OrganizationService organizationService,
                             ContactService contactService,
                             AuditService auditService) {
        this.organizationService = organizationService;
        this.contactService = contactService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public byte[] renderInvoicePdf(Invoice invoice) {
        Organization org = organizationService.getOrganizationEntity(invoice.getOrganizationId());
        Contact customer = contactService.requireContact(invoice.getOrganizationId(), invoice.getCustomerId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 42, 42, 44, 44);
        PdfWriter.getInstance(doc, out);
        doc.open();

        // Header table: Organization identity on left, document badge on right
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{60f, 40f});

        PdfPCell orgCell = borderlessCell();
        orgCell.addElement(new Paragraph(org.getName(), BRAND_FONT));
        if (org.getAddress() != null && !org.getAddress().isBlank()) {
            orgCell.addElement(new Paragraph(org.getAddress(), BODY_FONT));
        }
        StringBuilder orgContact = new StringBuilder();
        if (org.getContactEmail() != null && !org.getContactEmail().isBlank()) orgContact.append(org.getContactEmail());
        if (org.getContactPhone() != null && !org.getContactPhone().isBlank()) {
            if (!orgContact.isEmpty()) orgContact.append("  |  ");
            orgContact.append(org.getContactPhone());
        }
        if (!orgContact.isEmpty()) {
            orgCell.addElement(new Paragraph(orgContact.toString(), BODY_FONT));
        }
        if (org.getTaxId() != null && !org.getTaxId().isBlank()) {
            orgCell.addElement(new Paragraph("Tax ID: " + org.getTaxId(), BODY_FONT));
        }
        if (org.isDemo()) {
            orgCell.addElement(new Paragraph("DEMONSTRATION DATA ONLY", LABEL_FONT));
        }
        header.addCell(orgCell);

        PdfPCell metaCell = borderlessCell();
        metaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph title = new Paragraph("INVOICE", DOC_TITLE_FONT);
        title.setAlignment(Element.ALIGN_RIGHT);
        metaCell.addElement(title);
        addRightLine(metaCell, "Invoice No: " + invoice.getInvoiceNumber(), BODY_BOLD);
        addRightLine(metaCell, "Status: " + invoice.getStatus().name(), BODY_FONT);
        addRightLine(metaCell, "Issue Date: " + invoice.getIssueDate().format(DATE_FMT), BODY_FONT);
        addRightLine(metaCell, "Due Date: " + invoice.getDueDate().format(DATE_FMT), BODY_FONT);
        addRightLine(metaCell, "Currency: " + invoice.getCurrencyCode(), BODY_FONT);
        header.addCell(metaCell);
        header.setSpacingAfter(20f);
        doc.add(header);

        // Bill To block
        PdfPTable billTo = new PdfPTable(1);
        billTo.setWidthPercentage(100);
        PdfPCell billCell = new PdfPCell();
        billCell.setBackgroundColor(SAGE_BG);
        billCell.setBorderColor(BORDER);
        billCell.setPadding(10f);
        billCell.addElement(new Paragraph("BILL TO", LABEL_FONT));
        billCell.addElement(new Paragraph(customer.getName(), BODY_BOLD));
        if (customer.getContactPerson() != null && !customer.getContactPerson().isBlank()) {
            billCell.addElement(new Paragraph("Attn: " + customer.getContactPerson(), BODY_FONT));
        }
        if (customer.getBillingAddress() != null && !customer.getBillingAddress().isBlank()) {
            billCell.addElement(new Paragraph(customer.getBillingAddress(), BODY_FONT));
        }
        if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
            billCell.addElement(new Paragraph(customer.getEmail(), BODY_FONT));
        }
        billTo.addCell(billCell);
        billTo.setSpacingAfter(18f);
        doc.add(billTo);

        // Line items table with repeating header across pages
        PdfPTable linesTable = new PdfPTable(6);
        linesTable.setWidthPercentage(100);
        linesTable.setWidths(new float[]{36f, 10f, 15f, 12f, 11f, 16f});
        linesTable.setHeaderRows(1);

        addHeaderCell(linesTable, "Description", Element.ALIGN_LEFT);
        addHeaderCell(linesTable, "Qty", Element.ALIGN_RIGHT);
        addHeaderCell(linesTable, "Unit Price", Element.ALIGN_RIGHT);
        addHeaderCell(linesTable, "Discount", Element.ALIGN_RIGHT);
        addHeaderCell(linesTable, "Tax", Element.ALIGN_RIGHT);
        addHeaderCell(linesTable, "Total", Element.ALIGN_RIGHT);

        String ccy = invoice.getCurrencyCode();
        for (InvoiceLine line : invoice.getLines()) {
            addBodyCell(linesTable, line.getDescription(), Element.ALIGN_LEFT);
            addBodyCell(linesTable, line.getQuantity().stripTrailingZeros().toPlainString(), Element.ALIGN_RIGHT);
            addBodyCell(linesTable, formatAmount(line.getUnitPrice(), ccy), Element.ALIGN_RIGHT);
            addBodyCell(linesTable, formatAmount(line.getDiscountAmount(), ccy), Element.ALIGN_RIGHT);
            addBodyCell(linesTable, formatAmount(line.getTaxAmount(), ccy), Element.ALIGN_RIGHT);
            addBodyCell(linesTable, formatAmount(line.getLineTotal(), ccy), Element.ALIGN_RIGHT);
        }
        linesTable.setSpacingAfter(16f);
        doc.add(linesTable);

        // Totals block kept together so totals are never split across pages
        PdfPTable totalsWrapper = new PdfPTable(2);
        totalsWrapper.setWidthPercentage(100);
        totalsWrapper.setWidths(new float[]{54f, 46f});
        totalsWrapper.setKeepTogether(true);

        PdfPCell notesCell = borderlessCell();
        if (invoice.getPaymentInstructions() != null && !invoice.getPaymentInstructions().isBlank()) {
            notesCell.addElement(new Paragraph("PAYMENT INSTRUCTIONS", LABEL_FONT));
            notesCell.addElement(new Paragraph(invoice.getPaymentInstructions(), BODY_FONT));
        }
        if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
            Paragraph sp = new Paragraph("NOTES", LABEL_FONT);
            sp.setSpacingBefore(8f);
            notesCell.addElement(sp);
            notesCell.addElement(new Paragraph(invoice.getNotes(), BODY_FONT));
        }
        totalsWrapper.addCell(notesCell);

        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(100);
        totals.setWidths(new float[]{50f, 50f});
        addTotalRow(totals, "Subtotal", formatAmount(invoice.getSubtotal(), ccy), BODY_FONT);
        if (MoneyUtil.isPositive(invoice.getDiscountTotal())) {
            addTotalRow(totals, "Discount", "-" + formatAmount(invoice.getDiscountTotal(), ccy), BODY_FONT);
        }
        addTotalRow(totals, "Tax" + (invoice.isTaxInclusive() ? " (Incl.)" : ""), formatAmount(invoice.getTaxTotal(), ccy), BODY_FONT);
        addTotalRow(totals, "Total (" + ccy + ")", formatAmount(invoice.getTotalAmount(), ccy), TOTAL_FONT);
        addTotalRow(totals, "Amount Paid", formatAmount(invoice.getAmountPaid(), ccy), BODY_FONT);
        if (MoneyUtil.isPositive(invoice.getCreditApplied())) {
            addTotalRow(totals, "Credits Applied", formatAmount(invoice.getCreditApplied(), ccy), BODY_FONT);
        }
        addTotalRow(totals, "Balance Due", formatAmount(invoice.getOutstandingBalance(), ccy), TOTAL_FONT);

        PdfPCell totalsHost = borderlessCell();
        totalsHost.addElement(totals);
        totalsWrapper.addCell(totalsHost);
        doc.add(totalsWrapper);

        doc.close();
        auditService.record(invoice.getOrganizationId(), "DOCUMENT_EXPORTED", "INVOICE", invoice.getId().toString(),
                "Exported PDF for invoice " + invoice.getInvoiceNumber(), Map.of("format", "PDF"));
        return out.toByteArray();
    }

    public byte[] renderCreditNotePdf(CreditNote cn, Invoice invoice) {
        Organization org = organizationService.getOrganizationEntity(cn.getOrganizationId());
        Contact customer = contactService.requireContact(cn.getOrganizationId(), cn.getCustomerId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 42, 42, 44, 44);
        PdfWriter.getInstance(doc, out);
        doc.open();

        doc.add(new Paragraph(org.getName(), BRAND_FONT));
        Paragraph title = new Paragraph("CREDIT NOTE " + cn.getCreditNoteNumber(), DOC_TITLE_FONT);
        title.setSpacingBefore(8f);
        title.setSpacingAfter(14f);
        doc.add(title);

        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        addTotalRow(info, "Customer", customer.getName(), BODY_BOLD);
        addTotalRow(info, "Related Invoice", invoice.getInvoiceNumber(), BODY_FONT);
        addTotalRow(info, "Credit Date", cn.getCreditDate().format(DATE_FMT), BODY_FONT);
        addTotalRow(info, "Reason", cn.getReason(), BODY_FONT);
        addTotalRow(info, "Net Credit", formatAmount(cn.getNetAmount(), cn.getCurrencyCode()), BODY_FONT);
        addTotalRow(info, "Tax Credit", formatAmount(cn.getTaxAmount(), cn.getCurrencyCode()), BODY_FONT);
        addTotalRow(info, "Total Credit (" + cn.getCurrencyCode() + ")", formatAmount(cn.getTotalAmount(), cn.getCurrencyCode()), TOTAL_FONT);
        doc.add(info);

        doc.close();
        auditService.record(cn.getOrganizationId(), "DOCUMENT_EXPORTED", "CREDIT_NOTE", cn.getId().toString(),
                "Exported PDF for credit note " + cn.getCreditNoteNumber(), Map.of("format", "PDF"));
        return out.toByteArray();
    }

    public byte[] renderPaymentReceiptPdf(Payment payment) {
        Organization org = organizationService.getOrganizationEntity(payment.getOrganizationId());
        Contact contact = contactService.requireContact(payment.getOrganizationId(), payment.getContactId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 42, 42, 44, 44);
        PdfWriter.getInstance(doc, out);
        doc.open();

        doc.add(new Paragraph(org.getName(), BRAND_FONT));
        Paragraph title = new Paragraph("PAYMENT RECEIPT " + payment.getPaymentNumber(), DOC_TITLE_FONT);
        title.setSpacingBefore(8f);
        title.setSpacingAfter(14f);
        doc.add(title);

        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        addTotalRow(info, "Contact", contact.getName(), BODY_BOLD);
        addTotalRow(info, "Payment Type", payment.getPaymentType().name(), BODY_FONT);
        addTotalRow(info, "Payment Date", payment.getPaymentDate().format(DATE_FMT), BODY_FONT);
        addTotalRow(info, "Method", payment.getPaymentMethod().name(), BODY_FONT);
        if (payment.getReferenceNumber() != null && !payment.getReferenceNumber().isBlank()) {
            addTotalRow(info, "Reference", payment.getReferenceNumber(), BODY_FONT);
        }
        addTotalRow(info, "Amount (" + payment.getCurrencyCode() + ")", formatAmount(payment.getAmount(), payment.getCurrencyCode()), TOTAL_FONT);
        addTotalRow(info, "Allocated", formatAmount(payment.getAllocatedAmount(), payment.getCurrencyCode()), BODY_FONT);
        addTotalRow(info, "Unallocated Credit", formatAmount(payment.getUnallocatedAmount(), payment.getCurrencyCode()), BODY_FONT);
        doc.add(info);

        doc.close();
        auditService.record(payment.getOrganizationId(), "DOCUMENT_EXPORTED", "PAYMENT", payment.getId().toString(),
                "Exported receipt PDF for payment " + payment.getPaymentNumber(), Map.of("format", "PDF"));
        return out.toByteArray();
    }

    private PdfPCell borderlessCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(2f);
        return cell;
    }

    private void addRightLine(PdfPCell cell, String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        cell.addElement(p);
    }

    private void addHeaderCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_HEADER_FONT));
        cell.setBackgroundColor(FOREST);
        cell.setBorderColor(FOREST);
        cell.setHorizontalAlignment(align);
        cell.setPadding(7f);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, BODY_FONT));
        cell.setBorderColor(BORDER);
        cell.setHorizontalAlignment(align);
        cell.setPadding(6f);
        table.addCell(cell);
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell l = new PdfPCell(new Phrase(label, font));
        l.setBorder(Rectangle.BOTTOM);
        l.setBorderColor(BORDER);
        l.setPadding(6f);
        table.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, font));
        v.setBorder(Rectangle.BOTTOM);
        v.setBorderColor(BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPadding(6f);
        table.addCell(v);
    }

    public static String formatAmount(BigDecimal amount, String currencyCode) {
        int minor = MoneyUtil.minorUnits(currencyCode);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        String pattern = minor == 0 ? "#,##0" : "#,##0.00";
        DecimalFormat df = new DecimalFormat(pattern, symbols);
        BigDecimal safe = amount != null ? amount : BigDecimal.ZERO;
        return currencyCode + " " + df.format(safe);
    }
}
