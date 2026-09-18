package net.ddns.lexdev.systempro_api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ClientRequestDto(

    @NotNull
    @Valid
    PersonRequestDto person,

    @Valid
    IndividualPersonRequestDto individual,

    @Valid
    LegalEntityRequestDto legalEntity,

    @Valid
    List<PersonContactRequestDto> contacts,

    @Valid
    List<PersonAddressRequestDto> addresses,

    Boolean active

) {}