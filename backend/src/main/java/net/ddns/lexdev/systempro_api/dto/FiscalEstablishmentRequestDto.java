package net.ddns.lexdev.systempro_api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import net.ddns.lexdev.systempro_api.enums.FiscalEnvironment;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;

public record FiscalEstablishmentRequestDto(
    @NotNull Long personId,
    @NotBlank @Pattern(regexp = "\\d{7}") String municipalityIbgeCode,
    @NotNull TaxRegime taxRegime,
    @NotNull FiscalEnvironment environment,
    @Min(1) @Max(999) int series,
    @Min(1) long nextNumber,
    @Min(1) @Max(999999) Integer cscId,
    @Size(min = 16, max = 36) String csc,
    @Size(max = 500) String certificatePassword
) {}
