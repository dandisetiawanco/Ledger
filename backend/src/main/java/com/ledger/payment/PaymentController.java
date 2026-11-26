package com.ledger.payment;

import com.ledger.invoice.InvoicePdfService;
import com.ledger.shared.LedgerPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final InvoicePdfService pdfService;

    public PaymentController(PaymentService paymentService, InvoicePdfService pdfService) {
        this.paymentService = paymentService;
        this.pdfService = pdfService;
    }

    @GetMapping
    public List<PaymentService.PaymentDto> list(@RequestParam(required = false) PaymentType type) {
        return paymentService.listPayments(LedgerPrincipal.current().requireOrganizationId(), type);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentService.PaymentDto record(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentService.RecordPaymentRequest req
    ) {
        return paymentService.recordPayment(LedgerPrincipal.current(), req, idempotencyKey);
    }

    @PostMapping("/{paymentId}/reverse")
    public PaymentService.PaymentDto reverse(
            @PathVariable UUID paymentId,
            @RequestBody(required = false) ReversePaymentRequest req
    ) {
        return paymentService.reversePayment(LedgerPrincipal.current(), paymentId, req != null ? req.reason() : null);
    }

    @GetMapping("/{paymentId}/receipt.pdf")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable UUID paymentId) {
        UUID orgId = LedgerPrincipal.current().requireOrganizationId();
        Payment payment = paymentService.requirePaymentEntity(orgId, paymentId);
        byte[] pdf = pdfService.renderPaymentReceiptPdf(payment);
        ContentDisposition cd = ContentDisposition.inline()
                .filename(payment.getPaymentNumber() + "-receipt.pdf", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    public record ReversePaymentRequest(String reason) {}
}
