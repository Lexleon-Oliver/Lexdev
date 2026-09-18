package net.ddns.lexdev.systempro_api.dto;

import net.ddns.lexdev.systempro_api.domain.PersonAddress;

public record PersonAddressResponseDto(
    Long id,
    String type,
    String cep,
    String logradouro,
    String numero,
    String complemento,
    String bairro,
    String cidade,
    String uf,
    Boolean principal
) {

    public static PersonAddressResponseDto fromEntity(
        PersonAddress address
    ) {
        return new PersonAddressResponseDto(
            address.getId(),
            address.getType() != null
                ? address.getType().name()
                : null,
            address.getCep(),
            address.getLogradouro(),
            address.getNumero(),
            address.getComplemento(),
            address.getBairro(),
            address.getCidade(),
            address.getUf(),
            address.isPrincipal()
        );
    }
}
