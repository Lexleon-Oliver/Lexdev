package net.ddns.lexdev.systempro_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PersonRequestDto(

    @NotBlank
    String cpfCnpj,

    @NotBlank
    String tipoPessoa,

    @NotBlank
    String name,

    String nomeFantasia,

    String rgIe,

    @Email
    String email,

    String phone,

    String cep,

    String logradouro,

    String numero,

    String complemento,

    String bairro,

    String cidade,

    String uf

) {
}