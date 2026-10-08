package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductFiscalProfileRequestDto(
    @NotBlank @Pattern(regexp = "\\d{4}", message = "CFOP deve possuir 4 dígitos") String cfop,
    @NotBlank @Pattern(regexp = "\\d{2,3}", message = "CST/CSOSN deve possuir 2 ou 3 dígitos") String icmsCstCsosn,
    @NotBlank @Pattern(regexp = "\\d{2}", message = "CST PIS deve possuir 2 dígitos") String pisCst,
    @NotBlank @Pattern(regexp = "\\d{2}", message = "CST COFINS deve possuir 2 dígitos") String cofinsCst,
    @DecimalMin("0.0") BigDecimal icmsRate,
    @DecimalMin("0.0") BigDecimal pisRate,
    @DecimalMin("0.0") BigDecimal cofinsRate,
    @Pattern(regexp = "\\d{3}", message = "CST IBS/CBS deve possuir 3 dígitos") String ibsCbsCst,
    @Pattern(regexp = "\\d{6}", message = "cClassTrib deve possuir 6 dígitos") String cClassTrib,
    @DecimalMin("0.0") BigDecimal ibsRate,
    @DecimalMin("0.0") BigDecimal cbsRate,
    @Size(max = 5000) String additionalInformation
) {}