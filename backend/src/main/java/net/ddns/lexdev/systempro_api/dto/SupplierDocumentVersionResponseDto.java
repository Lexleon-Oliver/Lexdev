package net.ddns.lexdev.systempro_api.dto;

import net.ddns.lexdev.systempro_api.domain.SupplierDocumentVersion;
import net.ddns.lexdev.systempro_api.domain.DocumentScanStatus;

import java.time.Instant;

public record SupplierDocumentVersionResponseDto(
    Long id,
    Integer versionNumber,
    String originalFileName,
    String contentType,
    Long fileSize,
    String checksumSha256,
    DocumentScanStatus scanStatus,
    Instant createdAt
) {
    public static SupplierDocumentVersionResponseDto fromEntity(
        SupplierDocumentVersion version
    ) {
        return new SupplierDocumentVersionResponseDto(
            version.getId(),
            version.getVersionNumber(),
            version.getOriginalFileName(),
            version.getContentType(),
            version.getFileSize(),
            version.getChecksumSha256(),
            version.getScanStatus(),
            version.getCreatedAt()
        );
    }
}
