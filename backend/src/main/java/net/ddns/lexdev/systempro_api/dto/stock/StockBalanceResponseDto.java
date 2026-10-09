package net.ddns.lexdev.systempro_api.dto.stock;

import java.math.BigDecimal;

public record StockBalanceResponseDto(
    Long productId,
    String productCode,
    String productName,
    String unitOfMeasure,
    boolean controlsStock,
    boolean initialized,
    BigDecimal quantity
) {}