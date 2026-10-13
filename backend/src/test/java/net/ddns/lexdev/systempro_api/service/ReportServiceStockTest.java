package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import net.ddns.lexdev.systempro_api.dto.report.StockLevelStatus;
import net.ddns.lexdev.systempro_api.dto.report.StockReportResponseDto;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.repository.SaleRepository;
import net.ddns.lexdev.systempro_api.repository.projection.StockReportProjection;
import net.ddns.lexdev.systempro_api.repository.projection.StockReportSummaryProjection;

class ReportServiceStockTest {
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final SaleRepository saleRepository = mock(SaleRepository.class);
    private final ReportService service = new ReportService(productRepository, saleRepository);

    @Test
    void shouldUseDatabaseSummaryAndReturnPagedItems() {
        StockReportProjection low = row(1L, true, bd("3"), bd("5"), bd("20"), bd("8"));
        StockReportProjection normal = row(2L, true, bd("15"), bd("5"), bd("20"), bd("8"));
        StockReportSummaryProjection summary = summary(30L, 20L, 18L, 4L, "180", "150", "90", "70", "700");
        var pageable = PageRequest.of(0, 20);
        when(productRepository.stockReportSummary(any(), any())).thenReturn(summary);
        when(productRepository.stockReportItems(any(), any(), eq("produto"), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(low, normal), pageable, 30));

        StockReportResponseDto report = service.stock(LocalDate.of(2026,10,1), LocalDate.of(2026,10,9), " produto ", pageable);

        assertThat(report.products()).isEqualTo(30);
        assertThat(report.stockControlledProducts()).isEqualTo(20);
        assertThat(report.initializedStockProducts()).isEqualTo(18);
        assertThat(report.replenishmentNeededProducts()).isEqualTo(4);
        assertThat(report.currentBalance()).isEqualByComparingTo("180");
        assertThat(report.items().getTotalElements()).isEqualTo(30);
        assertThat(report.items().getContent()).hasSize(2);
        assertThat(report.items().getContent().getFirst().stockStatus()).isEqualTo(StockLevelStatus.BELOW_MINIMUM);
    }

    @Test
    void shouldClassifyNotInitializedZeroReorderAndAboveMaximum() {
        var rows = List.of(
            row(1L, true, null, bd("2"), bd("10"), bd("3")),
            row(2L, true, BigDecimal.ZERO, bd("2"), bd("10"), bd("3")),
            row(3L, true, bd("8"), bd("5"), bd("20"), bd("8")),
            row(4L, true, bd("21"), bd("5"), bd("20"), bd("8")),
            row(5L, false, null, null, null, null));
        StockReportSummaryProjection summary = summary(5L, 4L, 3L, 2L, "29", "0", "0", "0", "0");
        when(productRepository.stockReportSummary(any(), any())).thenReturn(summary);
        when(productRepository.stockReportItems(any(), any(), eq(""), any())).thenReturn(new PageImpl<>(rows));

        var report = service.stock(LocalDate.of(2026,10,1), LocalDate.of(2026,10,9), "", PageRequest.of(0,20));
        var items = report.items().getContent();
        assertThat(items.get(0).stockStatus()).isEqualTo(StockLevelStatus.NOT_INITIALIZED);
        assertThat(items.get(1).stockStatus()).isEqualTo(StockLevelStatus.OUT_OF_STOCK);
        assertThat(items.get(2).stockStatus()).isEqualTo(StockLevelStatus.REORDER);
        assertThat(items.get(3).stockStatus()).isEqualTo(StockLevelStatus.ABOVE_MAXIMUM);
        assertThat(items.get(4).stockStatus()).isEqualTo(StockLevelStatus.NOT_CONTROLLED);
    }

    private static StockReportProjection row(Long id, boolean controls, BigDecimal balance, BigDecimal minimum, BigDecimal maximum, BigDecimal reorder) {
        StockReportProjection row = mock(StockReportProjection.class);
        when(row.getProductId()).thenReturn(id); when(row.getCode()).thenReturn("P"+id); when(row.getName()).thenReturn("Produto "+id);
        when(row.getUnitOfMeasure()).thenReturn("UN"); when(row.getControlsStock()).thenReturn(controls); when(row.getCurrentBalance()).thenReturn(balance);
        when(row.getMinimumStock()).thenReturn(minimum); when(row.getMaximumStock()).thenReturn(maximum); when(row.getReorderPoint()).thenReturn(reorder);
        when(row.getSalePrice()).thenReturn(bd("10")); when(row.getStockEntries()).thenReturn(BigDecimal.ZERO); when(row.getStockOutputs()).thenReturn(BigDecimal.ZERO);
        when(row.getQuantitySold()).thenReturn(BigDecimal.ZERO); when(row.getSalesValue()).thenReturn(BigDecimal.ZERO); return row;
    }
    private static StockReportSummaryProjection summary(Long products, Long controlled, Long initialized, Long replenishment, String balance, String entries, String outputs, String sold, String value) {
        StockReportSummaryProjection s = mock(StockReportSummaryProjection.class);
        when(s.getProducts()).thenReturn(products); when(s.getStockControlledProducts()).thenReturn(controlled); when(s.getInitializedStockProducts()).thenReturn(initialized);
        when(s.getReplenishmentNeededProducts()).thenReturn(replenishment); when(s.getCurrentBalance()).thenReturn(bd(balance)); when(s.getStockEntries()).thenReturn(bd(entries));
        when(s.getStockOutputs()).thenReturn(bd(outputs)); when(s.getQuantitySold()).thenReturn(bd(sold)); when(s.getSalesValue()).thenReturn(bd(value)); return s;
    }
    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}