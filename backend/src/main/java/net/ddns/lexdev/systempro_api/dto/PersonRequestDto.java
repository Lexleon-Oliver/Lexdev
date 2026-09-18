package net.ddns.lexdev.systempro_api.dto;

import jakarta.validation.constraints.NotBlank;

public record PersonRequestDto(

    @NotBlank
    String cpfCnpj,

    @NotBlank
    String tipoPessoa,

    @NotBlank
    String name

) {}