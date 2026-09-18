package net.ddns.lexdev.systempro_api.dto;

import net.ddns.lexdev.systempro_api.domain.Client;
import net.ddns.lexdev.systempro_api.domain.Person;

public record ClientResponseDto(

    Long id,
    Long personId,
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

        Person p = client.getPerson();

        return new ClientResponseDto(
            client.getId(),
            p.getId(),
            p.getTipoPessoa() != null ? p.getTipoPessoa().name() : null,
            p.getName(),
            p.getNomeFantasia(),
            p.getCpfCnpj(),
            p.getRgIe(),
            p.getEmail(),
            p.getPhone(),
            p.getCep(),
            p.getLogradouro(),
            p.getNumero(),
            p.getComplemento(),
            p.getBairro(),
            p.getCidade(),
            p.getUf(),
            client.isActive()
        );
    }
}