package net.ddns.lexdev.systempro_api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CompanyRequestDto(
    @NotNull @Valid PersonRequestDto person,
    @NotNull @Valid LegalEntityRequestDto legalEntity,
    @Valid List<PersonContactRequestDto> contacts,
    @Valid List<PersonAddressRequestDto> addresses
) {}
