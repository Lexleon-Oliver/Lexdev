package net.ddns.lexdev.systempro_api.dto;

public record PersonAddressRequestDto(

    String type,

    String cep,

    String logradouro,

    String numero,

    String complemento,

    String bairro,

    String cidade,

    String uf,

    Boolean principal

) {}
