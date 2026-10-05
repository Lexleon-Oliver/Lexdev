package net.ddns.lexdev.systempro_api.service;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.config.StorageProperties;
import net.ddns.lexdev.systempro_api.domain.DocumentScanStatus;
import net.ddns.lexdev.systempro_api.domain.Supplier;
import net.ddns.lexdev.systempro_api.domain.SupplierDocument;
import net.ddns.lexdev.systempro_api.domain.SupplierDocumentVersion;
import net.ddns.lexdev.systempro_api.dto.SupplierDocumentDetailResponseDto;
import net.ddns.lexdev.systempro_api.dto.SupplierDocumentUploadRequestDto;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.SupplierDocumentRepository;
import net.ddns.lexdev.systempro_api.repository.SupplierDocumentVersionRepository;
import net.ddns.lexdev.systempro_api.repository.SupplierRepository;
import net.ddns.lexdev.systempro_api.storage.FileStorageService;
import net.ddns.lexdev.systempro_api.storage.FileValidationService;
import net.ddns.lexdev.systempro_api.storage.MalwareScanner;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class SupplierDocumentService {

    private final SupplierRepository supplierRepository;
    private final SupplierDocumentRepository documentRepository;
    private final SupplierDocumentVersionRepository versionRepository;
    private final FileValidationService fileValidationService;
    private final MalwareScanner malwareScanner;
    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate transactionTemplate;

    public SupplierDocumentService(
        SupplierRepository supplierRepository,
        SupplierDocumentRepository documentRepository,
        SupplierDocumentVersionRepository versionRepository,
        FileValidationService fileValidationService,
        MalwareScanner malwareScanner,
        FileStorageService fileStorageService,
        StorageProperties storageProperties,
        CurrentUserProvider currentUserProvider,
        PlatformTransactionManager transactionManager
    ) {
        this.supplierRepository = supplierRepository;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.fileValidationService = fileValidationService;
        this.malwareScanner = malwareScanner;
        this.fileStorageService = fileStorageService;
        this.storageProperties = storageProperties;
        this.currentUserProvider = currentUserProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public List<SupplierDocumentDetailResponseDto> findAll(Long supplierId) {
        ensureSupplierExists(supplierId);
        return documentRepository.findAllWithVersions(supplierId)
            .stream()
            .map(SupplierDocumentDetailResponseDto::fromEntity)
            .toList();
    }

    public SupplierDocumentDetailResponseDto findById(
        Long supplierId,
        Long documentId
    ) {
        SupplierDocument document = documentRepository
            .findDetailed(documentId, supplierId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Documento do fornecedor não encontrado."
            ));

        return SupplierDocumentDetailResponseDto.fromEntity(document);
    }

    public SupplierDocumentDetailResponseDto uploadNewDocument(
        Long supplierId,
        SupplierDocumentUploadRequestDto metadata,
        MultipartFile file
    ) {
        FileValidationService.ValidatedFile validated =
            fileValidationService.validate(
                file,
                storageProperties.maxFileSizeBytes()
            );

        try {
            PreparedUpload prepared = prepareNewDocument(
                supplierId,
                metadata,
                validated
            );
            return processUpload(prepared, validated, true);
        } finally {
            deleteTempFile(validated.path());
        }
    }

    public SupplierDocumentDetailResponseDto uploadNewVersion(
        Long supplierId,
        Long documentId,
        SupplierDocumentUploadRequestDto metadata,
        MultipartFile file
    ) {
        FileValidationService.ValidatedFile validated =
            fileValidationService.validate(
                file,
                storageProperties.maxFileSizeBytes()
            );

        try {
            PreparedUpload prepared = prepareNewVersion(
                supplierId,
                documentId,
                metadata,
                validated
            );
            return processUpload(prepared, validated, false);
        } finally {
            deleteTempFile(validated.path());
        }
    }

    public void delete(Long supplierId, Long documentId) {
        transactionTemplate.executeWithoutResult(status -> {
            SupplierDocument document = documentRepository
                .findForUpdate(documentId, supplierId)
                .orElseThrow(() -> new EntityNotFoundException(
                    "Documento do fornecedor não encontrado."
                ));

            documentRepository.delete(document);
        });
    }

    public DownloadedDocument downloadLatest(
        Long supplierId,
        Long documentId
    ) {
        ensureDocumentExists(supplierId, documentId);

        SupplierDocumentVersion version = versionRepository
            .findLatest(supplierId, documentId)
            .orElseThrow(() -> new EntityNotFoundException(
                "O documento não possui uma versão física disponível."
            ));

        ensureClean(version);
        return toDownloadedDocument(version);
    }

    public DownloadedDocument downloadVersion(
        Long supplierId,
        Long documentId,
        Long versionId
    ) {
        SupplierDocumentVersion version = versionRepository
            .findForDownload(
                supplierId,
                documentId,
                versionId,
                DocumentScanStatus.CLEAN
            )
            .orElseThrow(() -> new EntityNotFoundException(
                "Versão do documento não encontrada ou não está disponível."
            ));

        return toDownloadedDocument(version);
    }

    private PreparedUpload prepareNewDocument(
        Long supplierId,
        SupplierDocumentUploadRequestDto metadata,
        FileValidationService.ValidatedFile validated
    ) {
        String checksumSha256 = sha256(validated.path());
        return Objects.requireNonNull(
            transactionTemplate.execute(status -> {
                validateDates(metadata);
                Supplier supplier = supplierRepository
                    .findByIdWithPerson(supplierId)
                    .orElseThrow(() -> new EntityNotFoundException(
                        "Fornecedor não encontrado com o ID: " + supplierId
                    ));

                SupplierDocument document = new SupplierDocument(
                    supplier,
                    metadata.tipoDocumento().trim(),
                    normalizeNullable(metadata.numeroDocumento()),
                    metadata.dataEmissao(),
                    metadata.dataValidade()
                );

                documentRepository.save(document);
                documentRepository.flush();

                return createPendingVersion(
                    document,
                    supplierId,
                    validated,
                    checksumSha256
                );
            }),
            "Falha ao preparar o documento para upload."
        );
    }

    private PreparedUpload prepareNewVersion(
        Long supplierId,
        Long documentId,
        SupplierDocumentUploadRequestDto metadata,
        FileValidationService.ValidatedFile validated
    ) {
        String checksumSha256 = sha256(validated.path());
        return Objects.requireNonNull(
            transactionTemplate.execute(status -> {
                validateDates(metadata);

                SupplierDocument document = documentRepository
                    .findForUpdate(documentId, supplierId)
                    .orElseThrow(() -> new EntityNotFoundException(
                        "Documento do fornecedor não encontrado."
                    ));

                document.setTipoDocumento(metadata.tipoDocumento().trim());
                document.setNumeroDocumento(
                    normalizeNullable(metadata.numeroDocumento())
                );
                document.setDataEmissao(metadata.dataEmissao());
                document.setDataValidade(metadata.dataValidade());

                return createPendingVersion(
                    document,
                    supplierId,
                    validated,
                    checksumSha256
                );
            }),
            "Falha ao preparar a nova versão do documento."
        );
    }

    private PreparedUpload createPendingVersion(
        SupplierDocument document,
        Long supplierId,
        FileValidationService.ValidatedFile validated,
        String checksumSha256
    ) {
        int nextVersion = versionRepository.findMaxVersionNumber(
            document.getId()
        ) + 1;

        String storageKey = buildStorageKey(
            supplierId,
            document.getId(),
            validated.originalFileName()
        );

        SupplierDocumentVersion version = new SupplierDocumentVersion();
        version.setDocument(document);
        version.setVersionNumber(nextVersion);
        version.setOriginalFileName(validated.originalFileName());
        version.setStorageKey(storageKey);
        version.setContentType(validated.contentType());
        version.setFileSize(validated.fileSize());
        version.setChecksumSha256(checksumSha256);
        version.setScanStatus(DocumentScanStatus.PENDING_SCAN);
        version.setCreatedBy(currentUserProvider.requireUser());

        versionRepository.save(version);
        versionRepository.flush();

        return new PreparedUpload(
            supplierId,
            document.getId(),
            version.getId(),
            storageKey
        );
    }

    private SupplierDocumentDetailResponseDto processUpload(
        PreparedUpload prepared,
        FileValidationService.ValidatedFile validated,
        boolean newDocument
    ) {
        boolean stored = false;
        boolean finalized = false;

        try {
            fileStorageService.upload(
                prepared.storageKey(),
                validated.path(),
                validated.contentType()
            );
            stored = true;

            DocumentScanStatus scanStatus = malwareScanner.scan(
                validated.path()
            );

            finalizeScan(
                prepared.versionId(),
                scanStatus
            );
            finalized = true;

            return findById(
                prepared.supplierId(),
                prepared.documentId()
            );
        } catch (RuntimeException ex) {
            if (stored && !finalized) {
                try {
                    fileStorageService.delete(prepared.storageKey());
                } catch (RuntimeException cleanupFailure) {
                    ex.addSuppressed(cleanupFailure);
                }
            }

            if (!finalized) {
                try {
                    cleanupPendingUpload(prepared, newDocument);
                } catch (RuntimeException cleanupFailure) {
                    ex.addSuppressed(cleanupFailure);
                }
            }
            throw ex;
        }
    }

    private void finalizeScan(
        Long versionId,
        DocumentScanStatus scanStatus
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            SupplierDocumentVersion version = versionRepository
                .findById(versionId)
                .orElseThrow(() -> new EntityNotFoundException(
                    "Versão do documento não encontrada."
                ));

            version.setScanStatus(scanStatus);
        });
    }

    private void cleanupPendingUpload(
        PreparedUpload prepared,
        boolean newDocument
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            versionRepository.deleteById(prepared.versionId());

            if (newDocument) {
                documentRepository.findById(prepared.documentId())
                    .ifPresent(documentRepository::delete);
            }
        });
    }

    private DownloadedDocument toDownloadedDocument(
        SupplierDocumentVersion version
    ) {
        Resource resource = fileStorageService.download(
            version.getStorageKey()
        );
        MediaType mediaType = MediaType.parseMediaType(
            version.getContentType()
        );
        return new DownloadedDocument(
            resource,
            version.getOriginalFileName(),
            mediaType,
            version.getFileSize()
        );
    }

    private void ensureSupplierExists(Long supplierId) {
        if (!supplierRepository.existsById(supplierId)) {
            throw new EntityNotFoundException(
                "Fornecedor não encontrado com o ID: " + supplierId
            );
        }
    }

    private void ensureDocumentExists(
        Long supplierId,
        Long documentId
    ) {
        if (!documentRepository.existsByIdAndSupplierId(
            documentId,
            supplierId
        )) {
            throw new EntityNotFoundException(
                "Documento do fornecedor não encontrado."
            );
        }
    }

    private void ensureClean(SupplierDocumentVersion version) {
        if (version.getScanStatus() != DocumentScanStatus.CLEAN) {
            throw new BusinessException(
                "A versão mais recente do documento não está disponível para download."
            );
        }
    }

    private String buildStorageKey(
        Long supplierId,
        Long documentId,
        String originalFileName
    ) {
        String extension = fileValidationService.extensionOf(
            originalFileName
        );
        String suffix = extension.isBlank() ? "bin" : extension;
        return "suppliers/%d/documents/%d/versions/%s.%s".formatted(
            supplierId,
            documentId,
            UUID.randomUUID(),
            suffix
        );
    }

    private String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException(
                "Não foi possível calcular o checksum SHA-256.",
                e
            );
        }
    }

    private void validateDates(SupplierDocumentUploadRequestDto metadata) {
        LocalDate issueDate = metadata.dataEmissao();
        LocalDate expirationDate = metadata.dataValidade();

        if (issueDate != null
            && expirationDate != null
            && expirationDate.isBefore(issueDate)) {
            throw new BusinessException(
                "A data de validade não pode ser anterior à data de emissão."
            );
        }
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void deleteTempFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    public record DownloadedDocument(
        Resource resource,
        String originalFileName,
        MediaType mediaType,
        long contentLength
    ) {}

    private record PreparedUpload(
        Long supplierId,
        Long documentId,
        Long versionId,
        String storageKey
    ) {}
}
