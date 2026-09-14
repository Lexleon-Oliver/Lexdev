package net.ddns.lexdev.systempro_api.dto;

import java.time.LocalDate;

public record SupplierDocumentDto(
    String tipoDocumento,
    String numeroOuUrl,
    LocalDate dataValidade
) {}
