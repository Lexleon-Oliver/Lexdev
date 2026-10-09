package net.ddns.lexdev.systempro_api.dto.stock;

import java.math.BigDecimal;
import java.time.Instant;

import net.ddns.lexdev.systempro_api.enums.StockMovementOrigin;
import net.ddns.lexdev.systempro_api.enums.StockMovementType;

public record StockMovementResponseDto(
    Long id,
    Long productId,
    StockMovementType movementType,
    StockMovementOrigin origin,
    String sourceReference,
    BigDecimal quantity,
    BigDecimal previousBalance,
    BigDecimal resultingBalance,
    Long saleId,
    Long saleItemId,
    String reason,
    String createdBy,
    Instant createdAt
) {}