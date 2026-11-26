package com.ledger.document;

import com.ledger.shared.LedgerPrincipal;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentService.AttachmentDto upload(
            @RequestParam String entityType,
            @RequestParam UUID entityId,
            @RequestParam("file") MultipartFile file
    ) {
        return attachmentService.upload(LedgerPrincipal.current(), entityType, entityId, file);
    }

    @GetMapping
    public List<AttachmentService.AttachmentDto> list(
            @RequestParam String entityType,
            @RequestParam UUID entityId
    ) {
        return attachmentService.listForEntity(LedgerPrincipal.current().requireOrganizationId(), entityType, entityId);
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID attachmentId) {
        AttachmentService.DownloadPayload payload = attachmentService.loadForDownload(LedgerPrincipal.current(), attachmentId);
        ContentDisposition cd = ContentDisposition.attachment()
                .filename(payload.metadata().getOriginalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(payload.metadata().getContentType()))
                .body(payload.resource());
    }
}
