package net.ddns.lexdev.systempro_api.dto.dashboard;

import java.math.BigDecimal;

public record DashboardSalesDto(
    long count,
    BigDecimal grossSales,
    BigDecimal discounts,
    BigDecimal netSales,
    BigDecimal averageTicket,
    long cancelledSales
) {}