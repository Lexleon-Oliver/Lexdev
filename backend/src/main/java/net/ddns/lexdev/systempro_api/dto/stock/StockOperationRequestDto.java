package net.ddns.lexdev.systempro_api.dto.stock;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockOperationRequestDto(
    @NotNull @DecimalMin(value = "0.000001") BigDecimal quantity,
    @NotBlank @Size(max = 500) String reason,
    @NotBlank @Size(max = 120) String operationReference
) {}