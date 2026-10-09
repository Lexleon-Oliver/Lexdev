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
import net.ddns.lexdev.systempro_api.dto.report.StockReportItemDto;
import net.ddns.lexdev.systempro_api.dto.report.StockReportResponseDto;
import net.ddns.lexdev.systempro_api.enums.PaymentMethod;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.repository.SaleRepository;
import net.ddns.lexdev.systempro_api.repository.projection.FinancialSummaryProjection;

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
            .map(row -> new StockReportItemDto(
                number(row[0]).longValue(), (String) row[1], (String) row[2], (String) row[3],
                (Boolean) row[4], decimal(row[5]), decimal(row[6]), decimal(row[7]), decimal(row[8]),
                decimal(row[9]), decimal(row[10])
            )).toList();

        long controlled = items.stream().filter(StockReportItemDto::controlsStock).count();
        BigDecimal quantity = items.stream().map(StockReportItemDto::quantitySold).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal value = items.stream().map(StockReportItemDto::salesValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StockReportResponseDto(startDate, endDate, items.size(), controlled, quantity, value, items);
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

    private static BigDecimal decimal(Object value) { return value == null ? BigDecimal.ZERO : (BigDecimal) value; }
    private static Number number(Object value) { return value == null ? 0L : (Number) value; }
    private static BigDecimal zeroIfNull(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private record Period(Instant start, Instant endExclusive) {}
}