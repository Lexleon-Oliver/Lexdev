package net.ddns.lexdev.systempro_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SupplierDocumentUploadRequestDto(
    @NotBlank
    @Size(max = 100)
    String tipoDocumento,

    @Size(max = 100)
    String numeroDocumento,

    LocalDate dataEmissao,

    LocalDate dataValidade
) {}
