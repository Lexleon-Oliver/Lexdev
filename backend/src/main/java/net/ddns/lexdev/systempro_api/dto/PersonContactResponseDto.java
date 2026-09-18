package net.ddns.lexdev.systempro_api.dto;

import net.ddns.lexdev.systempro_api.domain.PersonContact;

public record PersonContactResponseDto(
    Long id,
    String type,
    String value,
    Boolean principal,
    String description
) {

    public static PersonContactResponseDto fromEntity(
        PersonContact contact
    ) {
        return new PersonContactResponseDto(
            contact.getId(),
            contact.getType() != null
                ? contact.getType().name()
                : null,
            contact.getValue(),
            contact.isPrincipal(),
            contact.getDescription()
        );
    }
}
