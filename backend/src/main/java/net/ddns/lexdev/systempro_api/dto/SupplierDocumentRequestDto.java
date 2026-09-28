package net.ddns.lexdev.systempro_api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierDocumentRequestDto(

    @NotBlank
    @Size(max = 100)
    String tipoDocumento,

    @Size(max = 100)
    String numeroDocumento,

    LocalDate dataValidade

) {}
