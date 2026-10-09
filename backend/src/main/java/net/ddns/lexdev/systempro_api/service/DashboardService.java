package net.ddns.lexdev.systempro_api.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardDailyDto;
import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardFiscalDto;
import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardPaymentDto;
import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardRecentSaleDto;
import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardResponseDto;
import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardSalesDto;
import net.ddns.lexdev.systempro_api.enums.PaymentMethod;
import net.ddns.lexdev.systempro_api.repository.FiscalDocumentRepository;
import net.ddns.lexdev.systempro_api.repository.SaleRepository;
import net.ddns.lexdev.systempro_api.repository.projection.DashboardFiscalProjection;
import net.ddns.lexdev.systempro_api.repository.projection.FinancialSummaryProjection;

@Service
public class DashboardService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final int RECENT_SALES_LIMIT = 10;
    private static final int PERFORMANCE_DAYS = 7;

    private final SaleRepository saleRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;

    public DashboardService(SaleRepository saleRepository, FiscalDocumentRepository fiscalDocumentRepository) {
        this.saleRepository = saleRepository;
        this.fiscalDocumentRepository = fiscalDocumentRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponseDto dashboard() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        Instant todayStart = today.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant tomorrowStart = today.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        LocalDate performanceStartDate = today.minusDays(PERFORMANCE_DAYS - 1L);
        Instant performanceStart = performanceStartDate.atStartOfDay(BUSINESS_ZONE).toInstant();

        FinancialSummaryProjection summary = saleRepository.financialSummary(todayStart, tomorrowStart);
        long salesCount = value(summary == null ? null : summary.getSales());
        BigDecimal grossSales = money(summary == null ? null : summary.getGrossSales());
        BigDecimal discounts = money(summary == null ? null : summary.getDiscounts());
        BigDecimal netSales = money(summary == null ? null : summary.getNetSales());
        BigDecimal averageTicket = salesCount == 0
            ? BigDecimal.ZERO
            : netSales.divide(BigDecimal.valueOf(salesCount), 4, RoundingMode.HALF_UP);

        DashboardSalesDto sales = new DashboardSalesDto(
            salesCount,
            grossSales,
            discounts,
            netSales,
            averageTicket,
            saleRepository.countCancelled(todayStart, tomorrowStart)
        );

        DashboardFiscalProjection fiscalSummary = fiscalDocumentRepository.dashboardFiscalSummary();
        long awaitingAuthorization = value(fiscalSummary == null ? null : fiscalSummary.getAwaitingAuthorization());
        long pendingConsultation = value(fiscalSummary == null ? null : fiscalSummary.getPendingConsultation());
        long offlineContingency = value(fiscalSummary == null ? null : fiscalSummary.getOfflineContingency());
        long pendingCancellation = value(fiscalSummary == null ? null : fiscalSummary.getPendingCancellation());
        DashboardFiscalDto fiscal = new DashboardFiscalDto(
            awaitingAuthorization + pendingConsultation + offlineContingency + pendingCancellation,
            awaitingAuthorization,
            pendingConsultation,
            offlineContingency,
            pendingCancellation
        );

        List<DashboardPaymentDto> payments = saleRepository.financialPayments(todayStart, tomorrowStart).stream()
            .map(row -> new DashboardPaymentDto(PaymentMethod.valueOf(row.getPaymentMethod()), money(row.getAmount())))
            .toList();

        List<DashboardRecentSaleDto> recentSales = saleRepository.dashboardRecentSales(PageRequest.of(0, RECENT_SALES_LIMIT)).stream()
            .map(row -> new DashboardRecentSaleDto(
                row.getId(), row.getSaleAt(), row.getStatus(), money(row.getTotal()), row.getFiscalStatus()
            ))
            .toList();

        Map<LocalDate, DashboardDailyDto> dailyByDate = new LinkedHashMap<>();
        for (int i = 0; i < PERFORMANCE_DAYS; i++) {
            LocalDate date = performanceStartDate.plusDays(i);
            dailyByDate.put(date, new DashboardDailyDto(date, 0L, BigDecimal.ZERO));
        }
        saleRepository.financialDaily(performanceStart, tomorrowStart).forEach(row -> {
            if (row.getDate() != null && dailyByDate.containsKey(row.getDate())) {
                dailyByDate.put(row.getDate(), new DashboardDailyDto(
                    row.getDate(), value(row.getSales()), money(row.getTotal())
                ));
            }
        });

        return new DashboardResponseDto(
            today,
            sales,
            fiscal,
            payments,
            recentSales,
            List.copyOf(dailyByDate.values())
        );
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}