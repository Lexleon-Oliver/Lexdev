package net.ddns.lexdev.systempro_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ClientRequestDto(
    @NotBlank(message = "Tipo de pessoa é obrigatório")
    String tipoPessoa,

    @NotBlank(message = "Nome é obrigatório")
    String name,

    String nomeFantasia,

    @NotBlank(message = "CPF/CNPJ é obrigatório")
    String cpfCnpj,

    String rgIe,

    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    String email,

    @NotBlank(message = "Telefone é obrigatório")
    String phone,

    String cep,
    String logradouro,
    String numero,
    String complemento,
    String bairro,
    String cidade,
    String uf,
    Boolean active
) {}