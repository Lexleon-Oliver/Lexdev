package net.ddns.lexdev.systempro_api.dto;

import net.ddns.lexdev.systempro_api.domain.Person;

public record PersonOptionResponseDto(
    Long id,
    String name,
    String cpfCnpj,
    String tradeName,
    String stateRegistration
) {
    public static PersonOptionResponseDto fromEntity(Person p) {
        var legal = p.getLegalEntity();
        return new PersonOptionResponseDto(
            p.getId(),
            p.getName(),
            p.getCpfCnpj(),
            legal == null ? null : legal.getNomeFantasia(),
            legal == null ? null : legal.getInscricaoEstadual()
        );
    }
}
