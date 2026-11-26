package com.ledger.document;

import com.ledger.audit.AuditService;
import com.ledger.identity.SessionAuthenticationFilter;
import com.ledger.shared.LedgerException;
import com.ledger.shared.LedgerPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp",
            "text/csv",
            "text/plain"
    );

    private static final Set<String> ALLOWED_ENTITY_TYPES = Set.of(
            "EXPENSE", "INVOICE", "PAYMENT", "ORGANIZATION_LOGO"
    );

    private final AttachmentRepository attachmentRepository;
    private final AuditService auditService;
    private final Path storageRoot;
    private final long maxFileBytes;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            AuditService auditService,
            @Value("${ledger.storage.root-dir:./uploads}") String rootDir,
            @Value("${ledger.storage.max-file-bytes:10485760}") long maxFileBytes) {
        this.attachmentRepository = attachmentRepository;
        this.auditService = auditService;
        this.storageRoot = Path.of(rootDir).toAbsolutePath().normalize();
        this.maxFileBytes = maxFileBytes;
    }

    @Transactional
    public AttachmentDto upload(LedgerPrincipal principal, String entityType, UUID entityId, MultipartFile file) {
        principal.requireFinanceOperations();
        UUID orgId = principal.requireOrganizationId();

        String normalizedType = entityType == null ? "" : entityType.trim().toUpperCase();
        if (!ALLOWED_ENTITY_TYPES.contains(normalizedType)) {
            throw LedgerException.badRequest("INVALID_ENTITY_TYPE", "Unsupported attachment entity type");
        }
        if (file == null || file.isEmpty()) {
            throw LedgerException.badRequest("EMPTY_FILE", "Uploaded file must not be empty");
        }
        if (file.getSize() > maxFileBytes) {
            throw LedgerException.badRequest("FILE_TOO_LARGE", "File exceeds maximum allowed size of " + (maxFileBytes / (1024 * 1024)) + " MB");
        }
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "application/octet-stream";
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw LedgerException.badRequest("UNSUPPORTED_FILE_TYPE", "Unsupported file type: " + contentType);
        }

        String safeOriginalName = sanitizeFilename(file.getOriginalFilename());
        String extension = extractSafeExtension(safeOriginalName, contentType);
        String storedName = orgId + "_" + UUID.randomUUID() + extension;

        Path orgDir = storageRoot.resolve(orgId.toString()).normalize();
        if (!orgDir.startsWith(storageRoot)) {
            throw LedgerException.forbidden("Invalid storage path");
        }
        Path targetPath = orgDir.resolve(storedName).normalize();
        if (!targetPath.startsWith(orgDir)) {
            throw LedgerException.forbidden("Path traversal rejected");
        }

        String sha256;
        try {
            Files.createDirectories(orgDir);
            byte[] bytes = file.getBytes();
            sha256 = sha256Hex(bytes);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist uploaded document", ex);
        }

        Attachment attachment = new Attachment(
                orgId, normalizedType, entityId, safeOriginalName, storedName,
                contentType, file.getSize(), sha256, principal.userId()
        );
        attachmentRepository.save(attachment);

        auditService.record(orgId, "DOCUMENT_UPLOADED", normalizedType, entityId.toString(),
                "Uploaded attachment " + safeOriginalName, Map.of("attachmentId", attachment.getId().toString()));
        return AttachmentDto.from(attachment);
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> listForEntity(UUID organizationId, String entityType, UUID entityId) {
        return attachmentRepository
                .findByOrganizationIdAndEntityTypeAndEntityIdOrderByUploadedAtDesc(organizationId, entityType.toUpperCase(), entityId)
                .stream()
                .map(AttachmentDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DownloadPayload loadForDownload(LedgerPrincipal principal, UUID attachmentId) {
        UUID orgId = principal.requireOrganizationId();
        Attachment attachment = attachmentRepository.findByIdAndOrganizationId(attachmentId, orgId)
                .orElseThrow(() -> LedgerException.notFound("Attachment not found"));

        Path orgDir = storageRoot.resolve(orgId.toString()).normalize();
        Path filePath = orgDir.resolve(attachment.getStoredFilename()).normalize();
        if (!filePath.startsWith(orgDir) || !Files.exists(filePath)) {
            throw LedgerException.notFound("Attachment file is unavailable");
        }
        try {
            Resource resource = new UrlResource(filePath.toUri());
            return new DownloadPayload(attachment, resource);
        } catch (IOException ex) {
            throw LedgerException.notFound("Attachment file cannot be read");
        }
    }

    private String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) {
            return "document";
        }
        String base = Path.of(raw).getFileName().toString();
        String cleaned = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.length() > 180 ? cleaned.substring(0, 180) : cleaned;
    }

    private String extractSafeExtension(String filename, String contentType) {
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            String ext = filename.substring(dot).toLowerCase();
            if (ext.matches("^\\.[a-z0-9]{1,6}$")) {
                return ext;
            }
        }
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "text/csv" -> ".csv";
            default -> ".bin";
        };
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record AttachmentDto(
            UUID id,
            String entityType,
            UUID entityId,
            String originalFilename,
            String contentType,
            long fileSize,
            java.time.Instant uploadedAt
    ) {
        static AttachmentDto from(Attachment a) {
            return new AttachmentDto(
                    a.getId(), a.getEntityType(), a.getEntityId(),
                    a.getOriginalFilename(), a.getContentType(), a.getFileSize(), a.getUploadedAt()
            );
        }
    }

    public record DownloadPayload(Attachment metadata, Resource resource) {}
}
