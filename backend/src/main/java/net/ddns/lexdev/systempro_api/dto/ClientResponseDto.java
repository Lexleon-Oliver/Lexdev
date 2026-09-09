package net.ddns.lexdev.systempro_api.dto;

import net.ddns.lexdev.systempro_api.domain.Client;

public record ClientResponseDto(
    Long id,
    String tipoPessoa,
    String name,
    String nomeFantasia,
    String cpfCnpj,
    String rgIe,
    String email,
    String phone,
    String cep,
    String logradouro,
    String numero,
    String complemento,
    String bairro,
    String cidade,
    String uf,
    Boolean active
) {
    public static ClientResponseDto fromEntity(Client client) {
        return new ClientResponseDto(
            client.getId(),
            client.getTipoPessoa(),
            client.getName(),
            client.getNomeFantasia(),
            client.getCpfCnpj(),
            client.getRgIe(),
            client.getEmail(),
            client.getPhone(),
            client.getCep(),
            client.getLogradouro(),
            client.getNumero(),
            client.getComplemento(),
            client.getBairro(),
            client.getCidade(),
            client.getUf(),
            client.getActive()
        );
    }
}
