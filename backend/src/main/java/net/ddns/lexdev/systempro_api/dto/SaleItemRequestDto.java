package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record SaleItemRequestDto(
    @NotNull Long productId,
    @NotNull @DecimalMin("0.000001") BigDecimal quantity,
    @DecimalMin("0.0") BigDecimal unitPrice,
    @DecimalMin("0.0") BigDecimal discount
) {}
