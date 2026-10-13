package net.ddns.lexdev.systempro_api.dto.stock;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockSettingsDto(
    @NotNull @Min(0) @Max(3650) Integer minSellableShelfLifeDays
) {}
