package net.ddns.lexdev.systempro_api.dto;

import net.ddns.lexdev.systempro_api.domain.IndividualPerson;

public record IndividualPersonResponseDto(
    Long id,
    String rg
) {

    public static IndividualPersonResponseDto fromEntity(
        IndividualPerson individual
    ) {
        if (individual == null) {
            return null;
        }

        return new IndividualPersonResponseDto(
            individual.getId(),
            individual.getRg()
        );
    }
}
