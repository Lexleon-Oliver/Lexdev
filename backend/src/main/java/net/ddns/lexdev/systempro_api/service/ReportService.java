package net.ddns.lexdev.systempro_api.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.ddns.lexdev.systempro_api.dto.report.FinancialDailyDto;
import net.ddns.lexdev.systempro_api.dto.report.FinancialPaymentDto;
import net.ddns.lexdev.systempro_api.dto.report.FinancialReportResponseDto;
import net.ddns.lexdev.systempro_api.dto.report.StockLevelStatus;
import net.ddns.lexdev.systempro_api.dto.report.StockReportItemDto;
import net.ddns.lexdev.systempro_api.dto.report.StockReportResponseDto;
import net.ddns.lexdev.systempro_api.enums.PaymentMethod;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.repository.SaleRepository;
import net.ddns.lexdev.systempro_api.repository.projection.FinancialSummaryProjection;
import net.ddns.lexdev.systempro_api.repository.projection.StockReportProjection;

@Service
public class ReportService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final int MAX_RANGE_DAYS = 366;

    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;

    public ReportService(ProductRepository productRepository, SaleRepository saleRepository) {
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
    }

    @Transactional(readOnly = true)
    public StockReportResponseDto stock(LocalDate startDate, LocalDate endDate) {
        Period period = period(startDate, endDate);
        List<StockReportItemDto> items = productRepository.stockReport(period.start(), period.endExclusive()).stream()
            .map(this::stockItem)
            .toList();

        long controlled = items.stream().filter(StockReportItemDto::controlsStock).count();
        long initialized = items.stream().filter(item -> item.controlsStock() && item.initialized()).count();
        long replenishmentNeeded = items.stream().filter(StockReportItemDto::replenishmentNeeded).count();
        BigDecimal currentBalance = items.stream()
            .filter(StockReportItemDto::initialized)
            .map(StockReportItemDto::currentBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal entries = items.stream().map(StockReportItemDto::stockEntries).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outputs = items.stream().map(StockReportItemDto::stockOutputs).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal quantity = items.stream().map(StockReportItemDto::quantitySold).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal value = items.stream().map(StockReportItemDto::salesValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StockReportResponseDto(startDate, endDate, items.size(), controlled, initialized,
            replenishmentNeeded, currentBalance, entries, outputs, quantity, value, items);
    }

    private StockReportItemDto stockItem(StockReportProjection row) {
        boolean controlsStock = Boolean.TRUE.equals(row.getControlsStock());
        boolean initialized = controlsStock && row.getCurrentBalance() != null;
        BigDecimal balance = initialized ? row.getCurrentBalance() : null;
        StockLevelStatus status = stockStatus(controlsStock, initialized, balance,
            row.getMinimumStock(), row.getMaximumStock(), row.getReorderPoint());
        boolean replenishmentNeeded = initialized && needsReplenishment(balance, row.getMinimumStock(), row.getReorderPoint());

        return new StockReportItemDto(
            row.getProductId(), row.getCode(), row.getName(), row.getUnitOfMeasure(), controlsStock,
            initialized, balance, status, replenishmentNeeded,
            row.getMinimumStock(), row.getMaximumStock(), row.getReorderPoint(), zeroIfNull(row.getSalePrice()),
            zeroIfNull(row.getStockEntries()), zeroIfNull(row.getStockOutputs()),
            zeroIfNull(row.getQuantitySold()), zeroIfNull(row.getSalesValue())
        );
    }

    private static StockLevelStatus stockStatus(
        boolean controlsStock,
        boolean initialized,
        BigDecimal balance,
        BigDecimal minimum,
        BigDecimal maximum,
        BigDecimal reorderPoint
    ) {
        if (!controlsStock) return StockLevelStatus.NOT_CONTROLLED;
        if (!initialized) return StockLevelStatus.NOT_INITIALIZED;
        if (balance.signum() == 0) return StockLevelStatus.OUT_OF_STOCK;
        if (minimum != null && balance.compareTo(minimum) < 0) return StockLevelStatus.BELOW_MINIMUM;
        if (reorderPoint != null && balance.compareTo(reorderPoint) <= 0) return StockLevelStatus.REORDER;
        if (maximum != null && balance.compareTo(maximum) > 0) return StockLevelStatus.ABOVE_MAXIMUM;
        return StockLevelStatus.NORMAL;
    }

    private static boolean needsReplenishment(BigDecimal balance, BigDecimal minimum, BigDecimal reorderPoint) {
        if (balance.signum() == 0) return true;
        if (minimum != null && balance.compareTo(minimum) < 0) return true;
        return reorderPoint != null && balance.compareTo(reorderPoint) <= 0;
    }

    @Transactional(readOnly = true)
    public FinancialReportResponseDto financial(LocalDate startDate, LocalDate endDate) {
        Period period = period(startDate, endDate);
        FinancialSummaryProjection summary = saleRepository.financialSummary(period.start(), period.endExclusive());
        long sales = summary.getSales() == null ? 0L : summary.getSales();
        BigDecimal gross = zeroIfNull(summary.getGrossSales());
        BigDecimal discounts = zeroIfNull(summary.getDiscounts());
        BigDecimal net = zeroIfNull(summary.getNetSales());
        BigDecimal average = sales == 0 ? BigDecimal.ZERO : net.divide(BigDecimal.valueOf(sales), 4, RoundingMode.HALF_UP);

        List<FinancialPaymentDto> payments = saleRepository.financialPayments(period.start(), period.endExclusive()).stream()
            .map(row -> new FinancialPaymentDto(PaymentMethod.valueOf(row.getPaymentMethod()), zeroIfNull(row.getAmount())))
            .toList();
        List<FinancialDailyDto> daily = saleRepository.financialDaily(period.start(), period.endExclusive()).stream()
            .map(row -> new FinancialDailyDto(row.getDate(), row.getSales() == null ? 0L : row.getSales(), zeroIfNull(row.getTotal())))
            .toList();

        return new FinancialReportResponseDto(startDate, endDate, sales,
            saleRepository.countCancelled(period.start(), period.endExclusive()), gross, discounts, net, average, payments, daily);
    }

    private Period period(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) throw new BusinessException("Informe as datas inicial e final do relatório.");
        if (endDate.isBefore(startDate)) throw new BusinessException("A data final não pode ser anterior à data inicial.");
        if (startDate.plusDays(MAX_RANGE_DAYS - 1L).isBefore(endDate)) throw new BusinessException("O período máximo para relatórios é de 366 dias.");
        return new Period(startDate.atStartOfDay(BUSINESS_ZONE).toInstant(), endDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant());
    }

    private static BigDecimal zeroIfNull(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private record Period(Instant start, Instant endExclusive) {}
}