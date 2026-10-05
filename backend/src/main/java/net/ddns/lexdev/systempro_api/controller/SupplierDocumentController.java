package net.ddns.lexdev.systempro_api.controller;

import jakarta.validation.Valid;
import net.ddns.lexdev.systempro_api.dto.SupplierDocumentDetailResponseDto;
import net.ddns.lexdev.systempro_api.dto.SupplierDocumentUploadRequestDto;
import net.ddns.lexdev.systempro_api.service.SupplierDocumentService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/suppliers/{supplierId}/documents")
public class SupplierDocumentController {

    private final SupplierDocumentService service;

    public SupplierDocumentController(SupplierDocumentService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<SupplierDocumentDetailResponseDto>> findAll(
        @PathVariable Long supplierId
    ) {
        return ResponseEntity.ok(service.findAll(supplierId));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<SupplierDocumentDetailResponseDto> findById(
        @PathVariable Long supplierId,
        @PathVariable Long documentId
    ) {
        return ResponseEntity.ok(service.findById(supplierId, documentId));
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<SupplierDocumentDetailResponseDto> upload(
        @PathVariable Long supplierId,
        @RequestPart("metadata") @Valid SupplierDocumentUploadRequestDto metadata,
        @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.status(201)
            .body(service.uploadNewDocument(supplierId, metadata, file));
    }

    @PostMapping(
        path = "/{documentId}/versions",
        consumes = "multipart/form-data"
    )
    public ResponseEntity<SupplierDocumentDetailResponseDto> uploadVersion(
        @PathVariable Long supplierId,
        @PathVariable Long documentId,
        @RequestPart("metadata") @Valid SupplierDocumentUploadRequestDto metadata,
        @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(
            service.uploadNewVersion(
                supplierId,
                documentId,
                metadata,
                file
            )
        );
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> downloadLatest(
        @PathVariable Long supplierId,
        @PathVariable Long documentId
    ) {
        return buildDownloadResponse(
            service.downloadLatest(supplierId, documentId)
        );
    }

    @GetMapping("/{documentId}/versions/{versionId}/download")
    public ResponseEntity<Resource> downloadVersion(
        @PathVariable Long supplierId,
        @PathVariable Long documentId,
        @PathVariable Long versionId
    ) {
        return buildDownloadResponse(
            service.downloadVersion(
                supplierId,
                documentId,
                versionId
            )
        );
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(
        @PathVariable Long supplierId,
        @PathVariable Long documentId
    ) {
        service.delete(supplierId, documentId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Resource> buildDownloadResponse(
        SupplierDocumentService.DownloadedDocument document
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setContentType(document.mediaType());
        headers.setContentLength(document.contentLength());
        headers.setContentDisposition(
            ContentDisposition.attachment()
                .filename(
                    document.originalFileName(),
                    StandardCharsets.UTF_8
                )
                .build()
        );

        return ResponseEntity.ok()
            .headers(headers)
            .body(document.resource());
    }
}
