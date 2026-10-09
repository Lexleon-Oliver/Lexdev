package net.ddns.lexdev.systempro_api.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StockReportResponseDto(
    LocalDate startDate,
    LocalDate endDate,
    long products,
    long stockControlledProducts,
    BigDecimal quantitySold,
    BigDecimal salesValue,
    List<StockReportItemDto> items
) {}