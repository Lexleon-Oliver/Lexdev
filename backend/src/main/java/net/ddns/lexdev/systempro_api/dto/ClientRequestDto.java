package net.ddns.lexdev.systempro_api.dto;

public record ClientRequestDto(
    PersonRequestDto person,
    Boolean active
) {}