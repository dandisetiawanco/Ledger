package com.ledger.invoice;

import com.ledger.shared.LedgerPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService pdfService;

    public InvoiceController(InvoiceService invoiceService, InvoicePdfService pdfService) {
        this.invoiceService = invoiceService;
        this.pdfService = pdfService;
    }

    @GetMapping
    public List<InvoiceService.InvoiceDto> list(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID customerId
    ) {
        return invoiceService.listInvoices(LedgerPrincipal.current().requireOrganizationId(), status, customerId);
    }

    @GetMapping("/{invoiceId}")
    public InvoiceService.InvoiceDto get(@PathVariable UUID invoiceId) {
        return invoiceService.getInvoice(LedgerPrincipal.current().requireOrganizationId(), invoiceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceService.InvoiceDto create(@Valid @RequestBody InvoiceService.CreateInvoiceRequest req) {
        return invoiceService.createDraft(LedgerPrincipal.current(), req);
    }

    @PatchMapping("/{invoiceId}")
    public InvoiceService.InvoiceDto update(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody InvoiceService.UpdateInvoiceRequest req
    ) {
        return invoiceService.updateDraft(LedgerPrincipal.current(), invoiceId, req);
    }

    @PostMapping("/{invoiceId}/issue")
    public InvoiceService.InvoiceDto issue(@PathVariable UUID invoiceId) {
        return invoiceService.issueInvoice(LedgerPrincipal.current(), invoiceId);
    }

    @PostMapping("/{invoiceId}/void")
    public InvoiceService.InvoiceDto voidInvoice(
            @PathVariable UUID invoiceId,
            @RequestBody(required = false) VoidInvoiceRequest req
    ) {
        return invoiceService.voidInvoice(LedgerPrincipal.current(), invoiceId, req != null ? req.reason() : null);
    }

    @PostMapping("/{invoiceId}/credit-notes")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceService.CreditNoteDto createCreditNote(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody InvoiceService.CreateCreditNoteRequest req
    ) {
        return invoiceService.issueCreditNote(LedgerPrincipal.current(), invoiceId, req);
    }

    @GetMapping("/{invoiceId}/pdf")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable UUID invoiceId) {
        UUID orgId = LedgerPrincipal.current().requireOrganizationId();
        Invoice invoice = invoiceService.requireInvoiceEntity(orgId, invoiceId);
        byte[] pdf = pdfService.renderInvoicePdf(invoice);
        ContentDisposition cd = ContentDisposition.inline()
                .filename(invoice.getInvoiceNumber() + ".pdf", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/credit-notes/{creditNoteId}/pdf")
    public ResponseEntity<byte[]> downloadCreditNotePdf(@PathVariable UUID creditNoteId) {
        UUID orgId = LedgerPrincipal.current().requireOrganizationId();
        CreditNote cn = invoiceService.requireCreditNoteEntity(orgId, creditNoteId);
        Invoice invoice = invoiceService.requireInvoiceEntity(orgId, cn.getInvoiceId());
        byte[] pdf = pdfService.renderCreditNotePdf(cn, invoice);
        ContentDisposition cd = ContentDisposition.inline()
                .filename(cn.getCreditNoteNumber() + ".pdf", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    public record VoidInvoiceRequest(String reason) {}
}
