package net.ddns.lexdev.systempro_api.dto.report;

import java.math.BigDecimal;

public record StockReportItemDto(
    Long productId,
    String code,
    String name,
    String unitOfMeasure,
    boolean controlsStock,
    BigDecimal minimumStock,
    BigDecimal maximumStock,
    BigDecimal reorderPoint,
    BigDecimal salePrice,
    BigDecimal quantitySold,
    BigDecimal salesValue
) {}