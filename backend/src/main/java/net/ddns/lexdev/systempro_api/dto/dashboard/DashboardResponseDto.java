package net.ddns.lexdev.systempro_api.dto.dashboard;

import java.time.LocalDate;
import java.util.List;

public record DashboardResponseDto(
    LocalDate date,
    DashboardSalesDto sales,
    DashboardFiscalDto fiscal,
    List<DashboardPaymentDto> payments,
    List<DashboardRecentSaleDto> recentSales,
    List<DashboardDailyDto> dailyPerformance
) {}