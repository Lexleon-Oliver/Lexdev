package net.ddns.lexdev.systempro_api.dto.report;

import java.math.BigDecimal;

public record StockReportItemDto(
    Long productId,
    String code,
    String name,
    String unitOfMeasure,
    boolean controlsStock,
    boolean initialized,
    BigDecimal currentBalance,
    StockLevelStatus stockStatus,
    boolean replenishmentNeeded,
    BigDecimal minimumStock,
    BigDecimal maximumStock,
    BigDecimal reorderPoint,
    BigDecimal salePrice,
    BigDecimal stockEntries,
    BigDecimal stockOutputs,
    BigDecimal quantitySold,
    BigDecimal salesValue
) {}