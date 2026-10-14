package net.ddns.lexdev.systempro_api.dto;

import net.ddns.lexdev.systempro_api.repository.projection.SupplierSummaryProjection;

public record SupplierSummaryResponseDto(
    Long id,
    PersonResponseDto person,
    String email,
    String phone,
    String categoria
) {
    public static SupplierSummaryResponseDto fromProjection(SupplierSummaryProjection projection) {
        return new SupplierSummaryResponseDto(
            projection.getId(),
            new PersonResponseDto(
                projection.getPersonId(),
                projection.getTipoPessoa(),
                projection.getName(),
                projection.getCpfCnpj()
            ),
            projection.getEmail(),
            projection.getPhone(),
            projection.getCategoria()
        );
    }
}
