package net.ddns.lexdev.systempro_api.dto;

import java.time.LocalDate;

public record SupplierDocumentResponseDto(

    String tipoDocumento,

    String numeroDocumento,

    LocalDate dataValidade

) {}
