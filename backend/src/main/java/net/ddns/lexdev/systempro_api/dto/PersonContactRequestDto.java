package net.ddns.lexdev.systempro_api.dto;

import jakarta.validation.constraints.NotBlank;

public record PersonContactRequestDto(

    @NotBlank
    String type,

    @NotBlank
    String value,

    Boolean principal,

    String description

) {}
