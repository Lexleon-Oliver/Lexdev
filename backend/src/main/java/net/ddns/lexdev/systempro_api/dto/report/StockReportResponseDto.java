package net.ddns.lexdev.systempro_api.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StockReportResponseDto(
    LocalDate startDate,
    LocalDate endDate,
    long products,
    long stockControlledProducts,
    long initializedStockProducts,
    long replenishmentNeededProducts,
    BigDecimal currentBalance,
    BigDecimal stockEntries,
    BigDecimal stockOutputs,
    BigDecimal quantitySold,
    BigDecimal salesValue,
    List<StockReportItemDto> items
) {}