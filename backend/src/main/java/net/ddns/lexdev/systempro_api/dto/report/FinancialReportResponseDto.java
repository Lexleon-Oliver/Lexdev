package net.ddns.lexdev.systempro_api.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FinancialReportResponseDto(
    LocalDate startDate,
    LocalDate endDate,
    long sales,
    long cancelledSales,
    BigDecimal grossSales,
    BigDecimal discounts,
    BigDecimal netSales,
    BigDecimal averageTicket,
    List<FinancialPaymentDto> payments,
    List<FinancialDailyDto> daily
) {}