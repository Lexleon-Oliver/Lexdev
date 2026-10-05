package net.ddns.lexdev.systempro_api.dto;

import net.ddns.lexdev.systempro_api.domain.SupplierDocument;
import net.ddns.lexdev.systempro_api.domain.SupplierDocumentVersion;

import java.time.LocalDate;
import java.util.List;

public record SupplierDocumentDetailResponseDto(
    Long id,
    String tipoDocumento,
    String numeroDocumento,
    LocalDate dataEmissao,
    LocalDate dataValidade,
    SupplierDocumentVersionResponseDto latestVersion,
    List<SupplierDocumentVersionResponseDto> versions
) {
    public static SupplierDocumentDetailResponseDto fromEntity(
        SupplierDocument document
    ) {
        List<SupplierDocumentVersionResponseDto> versions =
            document.getVersions()
                .stream()
                .map(SupplierDocumentVersionResponseDto::fromEntity)
                .toList();

        SupplierDocumentVersion latest = document.getVersions()
            .stream()
            .findFirst()
            .orElse(null);

        return new SupplierDocumentDetailResponseDto(
            document.getId(),
            document.getTipoDocumento(),
            document.getNumeroDocumento(),
            document.getDataEmissao(),
            document.getDataValidade(),
            latest == null
                ? null
                : SupplierDocumentVersionResponseDto.fromEntity(latest),
            versions
        );
    }
}
