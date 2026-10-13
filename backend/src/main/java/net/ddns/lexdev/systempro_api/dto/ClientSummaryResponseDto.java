package net.ddns.lexdev.systempro_api.dto;

import net.ddns.lexdev.systempro_api.repository.projection.ClientSummaryProjection;

public record ClientSummaryResponseDto(
    Long id,
    PersonResponseDto person,
    String email,
    String phone,
    Boolean active
) {
    public static ClientSummaryResponseDto fromProjection(ClientSummaryProjection projection) {
        return new ClientSummaryResponseDto(
            projection.getId(),
            new PersonResponseDto(
                projection.getPersonId(),
                projection.getTipoPessoa(),
                projection.getName(),
                projection.getCpfCnpj()
            ),
            projection.getEmail(),
            projection.getPhone(),
            projection.getActive()
        );
    }
}
