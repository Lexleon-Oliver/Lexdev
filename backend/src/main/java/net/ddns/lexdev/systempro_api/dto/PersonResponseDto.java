package net.ddns.lexdev.systempro_api.dto;

import net.ddns.lexdev.systempro_api.domain.Person;

public record PersonResponseDto(
    Long id,
    String tipoPessoa,
    String name,
    String cpfCnpj
) {

    public static PersonResponseDto fromEntity(Person person) {
        return new PersonResponseDto(
            person.getId(),
            person.getTipoPessoa() != null
                ? person.getTipoPessoa().name()
                : null,
            person.getName(),
            person.getCpfCnpj()
        );
    }
}
