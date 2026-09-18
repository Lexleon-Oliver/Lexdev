package net.ddns.lexdev.systempro_api.dto;

import net.ddns.lexdev.systempro_api.domain.LegalEntity;

public record LegalEntityResponseDto(
    Long id,
    String nomeFantasia,
    String inscricaoEstadual
) {

    public static LegalEntityResponseDto fromEntity(
        LegalEntity legalEntity
    ) {
        if (legalEntity == null) {
            return null;
        }

        return new LegalEntityResponseDto(
            legalEntity.getId(),
            legalEntity.getNomeFantasia(),
            legalEntity.getInscricaoEstadual()
        );
    }
}
