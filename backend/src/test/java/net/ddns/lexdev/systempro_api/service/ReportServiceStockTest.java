package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.dto.report.StockLevelStatus;
import net.ddns.lexdev.systempro_api.dto.report.StockReportResponseDto;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.repository.SaleRepository;
import net.ddns.lexdev.systempro_api.repository.projection.StockReportProjection;

class ReportServiceStockTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final SaleRepository saleRepository = mock(SaleRepository.class);
    private final ReportService service = new ReportService(productRepository, saleRepository);

    @Test
    void shouldConsolidatePhysicalBalanceMovementsSalesAndReplenishment() {
        StockReportProjection low = row(1L, true, bd("3"), bd("5"), bd("20"), bd("8"),
            bd("10"), bd("7"), bd("4"), bd("40"));
        StockReportProjection normal = row(2L, true, bd("15"), bd("5"), bd("20"), bd("8"),
            bd("5"), bd("2"), bd("2"), bd("20"));
        StockReportProjection uncontrolled = row(3L, false, null, null, null, null,
            bd("0"), bd("0"), bd("1"), bd("10"));

        when(productRepository.stockReport(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(List.of(low, normal, uncontrolled));

        StockReportResponseDto report = service.stock(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9));

        assertThat(report.products()).isEqualTo(3);
        assertThat(report.stockControlledProducts()).isEqualTo(2);
        assertThat(report.initializedStockProducts()).isEqualTo(2);
        assertThat(report.replenishmentNeededProducts()).isEqualTo(1);
        assertThat(report.currentBalance()).isEqualByComparingTo("18");
        assertThat(report.stockEntries()).isEqualByComparingTo("15");
        assertThat(report.stockOutputs()).isEqualByComparingTo("9");
        assertThat(report.quantitySold()).isEqualByComparingTo("7");
        assertThat(report.salesValue()).isEqualByComparingTo("70");
        assertThat(report.items().get(0).stockStatus()).isEqualTo(StockLevelStatus.BELOW_MINIMUM);
        assertThat(report.items().get(0).replenishmentNeeded()).isTrue();
        assertThat(report.items().get(2).stockStatus()).isEqualTo(StockLevelStatus.NOT_CONTROLLED);
        assertThat(report.items().get(2).currentBalance()).isNull();
    }

    @Test
    void shouldDistinguishNotInitializedFromZeroBalance() {
        StockReportProjection notInitialized = row(1L, true, null, bd("2"), bd("10"), bd("3"),
            bd("0"), bd("0"), bd("0"), bd("0"));
        StockReportProjection zero = row(2L, true, BigDecimal.ZERO, bd("2"), bd("10"), bd("3"),
            bd("0"), bd("0"), bd("0"), bd("0"));

        when(productRepository.stockReport(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(List.of(notInitialized, zero));

        StockReportResponseDto report = service.stock(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9));

        assertThat(report.initializedStockProducts()).isEqualTo(1);
        assertThat(report.replenishmentNeededProducts()).isEqualTo(1);
        assertThat(report.items().get(0).initialized()).isFalse();
        assertThat(report.items().get(0).stockStatus()).isEqualTo(StockLevelStatus.NOT_INITIALIZED);
        assertThat(report.items().get(1).initialized()).isTrue();
        assertThat(report.items().get(1).stockStatus()).isEqualTo(StockLevelStatus.OUT_OF_STOCK);
        assertThat(report.items().get(1).replenishmentNeeded()).isTrue();
    }

    @Test
    void shouldClassifyReorderAndAboveMaximumWithoutInventingThresholds() {
        StockReportProjection reorder = row(1L, true, bd("8"), bd("5"), bd("20"), bd("8"),
            bd("0"), bd("0"), bd("0"), bd("0"));
        StockReportProjection above = row(2L, true, bd("21"), bd("5"), bd("20"), bd("8"),
            bd("0"), bd("0"), bd("0"), bd("0"));
        StockReportProjection normalNoThresholds = row(3L, true, bd("1"), null, null, null,
            bd("0"), bd("0"), bd("0"), bd("0"));

        when(productRepository.stockReport(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(List.of(reorder, above, normalNoThresholds));

        StockReportResponseDto report = service.stock(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9));

        assertThat(report.items().get(0).stockStatus()).isEqualTo(StockLevelStatus.REORDER);
        assertThat(report.items().get(0).replenishmentNeeded()).isTrue();
        assertThat(report.items().get(1).stockStatus()).isEqualTo(StockLevelStatus.ABOVE_MAXIMUM);
        assertThat(report.items().get(1).replenishmentNeeded()).isFalse();
        assertThat(report.items().get(2).stockStatus()).isEqualTo(StockLevelStatus.NORMAL);
        assertThat(report.items().get(2).replenishmentNeeded()).isFalse();
    }

    private static StockReportProjection row(
        Long id, boolean controls, BigDecimal balance, BigDecimal minimum, BigDecimal maximum,
        BigDecimal reorder, BigDecimal entries, BigDecimal outputs, BigDecimal sold, BigDecimal salesValue
    ) {
        StockReportProjection row = mock(StockReportProjection.class);
        when(row.getProductId()).thenReturn(id);
        when(row.getCode()).thenReturn("P" + id);
        when(row.getName()).thenReturn("Produto " + id);
        when(row.getUnitOfMeasure()).thenReturn("UN");
        when(row.getControlsStock()).thenReturn(controls);
        when(row.getCurrentBalance()).thenReturn(balance);
        when(row.getMinimumStock()).thenReturn(minimum);
        when(row.getMaximumStock()).thenReturn(maximum);
        when(row.getReorderPoint()).thenReturn(reorder);
        when(row.getSalePrice()).thenReturn(bd("10"));
        when(row.getStockEntries()).thenReturn(entries);
        when(row.getStockOutputs()).thenReturn(outputs);
        when(row.getQuantitySold()).thenReturn(sold);
        when(row.getSalesValue()).thenReturn(salesValue);
        return row;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}