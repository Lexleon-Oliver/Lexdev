package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardResponseDto;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.PaymentMethod;
import net.ddns.lexdev.systempro_api.enums.SaleStatus;
import net.ddns.lexdev.systempro_api.repository.FiscalDocumentRepository;
import net.ddns.lexdev.systempro_api.repository.SaleRepository;
import net.ddns.lexdev.systempro_api.repository.projection.DashboardFiscalProjection;
import net.ddns.lexdev.systempro_api.repository.projection.DashboardRecentSaleProjection;
import net.ddns.lexdev.systempro_api.repository.projection.FinancialDailyProjection;
import net.ddns.lexdev.systempro_api.repository.projection.FinancialPaymentProjection;
import net.ddns.lexdev.systempro_api.repository.projection.FinancialSummaryProjection;

class DashboardServiceTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    @Test
    void deveConsolidarDashboardComMetricasFiscaisPagamentosEVendasRecentes() {
        SaleRepository saleRepository = mock(SaleRepository.class);
        FiscalDocumentRepository fiscalDocumentRepository = mock(FiscalDocumentRepository.class);
        DashboardService service = new DashboardService(saleRepository, fiscalDocumentRepository);
        LocalDate today = LocalDate.now(BUSINESS_ZONE);

        FinancialSummaryProjection summary = mock(FinancialSummaryProjection.class);
        when(summary.getSales()).thenReturn(4L);
        when(summary.getGrossSales()).thenReturn(new BigDecimal("500.0000"));
        when(summary.getDiscounts()).thenReturn(new BigDecimal("20.0000"));
        when(summary.getNetSales()).thenReturn(new BigDecimal("480.0000"));
        when(saleRepository.financialSummary(any(), any())).thenReturn(summary);
        when(saleRepository.countCancelled(any(), any())).thenReturn(1L);

        DashboardFiscalProjection fiscal = mock(DashboardFiscalProjection.class);
        when(fiscal.getAwaitingAuthorization()).thenReturn(2L);
        when(fiscal.getPendingConsultation()).thenReturn(1L);
        when(fiscal.getOfflineContingency()).thenReturn(3L);
        when(fiscal.getPendingCancellation()).thenReturn(1L);
        when(fiscalDocumentRepository.dashboardFiscalSummary()).thenReturn(fiscal);

        FinancialPaymentProjection payment = mock(FinancialPaymentProjection.class);
        when(payment.getPaymentMethod()).thenReturn(PaymentMethod.PIX.name());
        when(payment.getAmount()).thenReturn(new BigDecimal("480.0000"));
        when(saleRepository.financialPayments(any(), any())).thenReturn(List.of(payment));

        DashboardRecentSaleProjection recent = mock(DashboardRecentSaleProjection.class);
        when(recent.getId()).thenReturn(99L);
        when(recent.getSaleAt()).thenReturn(today.atTime(12, 0).atZone(BUSINESS_ZONE).toInstant());
        when(recent.getStatus()).thenReturn(SaleStatus.FISCALIZADA);
        when(recent.getTotal()).thenReturn(new BigDecimal("120.0000"));
        when(recent.getFiscalStatus()).thenReturn(FiscalDocumentStatus.AUTORIZADA);
        when(saleRepository.dashboardRecentSales(any(Pageable.class))).thenReturn(List.of(recent));
        when(saleRepository.financialDaily(any(), any())).thenReturn(List.of());

        DashboardResponseDto result = service.dashboard();

        assertThat(result.date()).isEqualTo(today);
        assertThat(result.sales().count()).isEqualTo(4);
        assertThat(result.sales().grossSales()).isEqualByComparingTo("500.0000");
        assertThat(result.sales().discounts()).isEqualByComparingTo("20.0000");
        assertThat(result.sales().netSales()).isEqualByComparingTo("480.0000");
        assertThat(result.sales().averageTicket()).isEqualByComparingTo("120.0000");
        assertThat(result.sales().cancelledSales()).isEqualTo(1);
        assertThat(result.fiscal().pending()).isEqualTo(7);
        assertThat(result.fiscal().awaitingAuthorization()).isEqualTo(2);
        assertThat(result.fiscal().pendingConsultation()).isEqualTo(1);
        assertThat(result.fiscal().offlineContingency()).isEqualTo(3);
        assertThat(result.fiscal().pendingCancellation()).isEqualTo(1);
        assertThat(result.payments()).hasSize(1);
        assertThat(result.payments().getFirst().paymentMethod()).isEqualTo(PaymentMethod.PIX);
        assertThat(result.recentSales()).hasSize(1);
        assertThat(result.recentSales().getFirst().fiscalStatus()).isEqualTo(FiscalDocumentStatus.AUTORIZADA);
        assertThat(result.dailyPerformance()).hasSize(7);
        assertThat(result.dailyPerformance()).allSatisfy(day -> {
            assertThat(day.sales()).isZero();
            assertThat(day.total()).isEqualByComparingTo(BigDecimal.ZERO);
        });

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(saleRepository).dashboardRecentSales(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void devePreencherOsSeteDiasIncluindoDiasSemVenda() {
        SaleRepository saleRepository = mock(SaleRepository.class);
        FiscalDocumentRepository fiscalDocumentRepository = mock(FiscalDocumentRepository.class);
        DashboardService service = new DashboardService(saleRepository, fiscalDocumentRepository);
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate saleDate = today.minusDays(3);

        when(saleRepository.financialSummary(any(), any())).thenReturn(mock(FinancialSummaryProjection.class));
        when(fiscalDocumentRepository.dashboardFiscalSummary()).thenReturn(mock(DashboardFiscalProjection.class));
        when(saleRepository.financialPayments(any(), any())).thenReturn(List.of());
        when(saleRepository.dashboardRecentSales(any(Pageable.class))).thenReturn(List.of());

        FinancialDailyProjection daily = mock(FinancialDailyProjection.class);
        when(daily.getDate()).thenReturn(saleDate);
        when(daily.getSales()).thenReturn(2L);
        when(daily.getTotal()).thenReturn(new BigDecimal("75.5000"));
        when(saleRepository.financialDaily(any(), any())).thenReturn(List.of(daily));

        DashboardResponseDto result = service.dashboard();

        assertThat(result.dailyPerformance()).hasSize(7);
        assertThat(result.dailyPerformance().getFirst().date()).isEqualTo(today.minusDays(6));
        assertThat(result.dailyPerformance().getLast().date()).isEqualTo(today);
        assertThat(result.dailyPerformance())
            .filteredOn(day -> day.date().equals(saleDate))
            .singleElement()
            .satisfies(day -> {
                assertThat(day.sales()).isEqualTo(2);
                assertThat(day.total()).isEqualByComparingTo("75.5000");
            });
    }

    @Test
    void deveRetornarZerosQuandoNaoHaMovimentacao() {
        SaleRepository saleRepository = mock(SaleRepository.class);
        FiscalDocumentRepository fiscalDocumentRepository = mock(FiscalDocumentRepository.class);
        DashboardService service = new DashboardService(saleRepository, fiscalDocumentRepository);

        when(saleRepository.financialSummary(any(), any())).thenReturn(null);
        when(fiscalDocumentRepository.dashboardFiscalSummary()).thenReturn(null);
        when(saleRepository.financialPayments(any(), any())).thenReturn(List.of());
        when(saleRepository.dashboardRecentSales(any(Pageable.class))).thenReturn(List.of());
        when(saleRepository.financialDaily(any(), any())).thenReturn(List.of());

        DashboardResponseDto result = service.dashboard();

        assertThat(result.sales().count()).isZero();
        assertThat(result.sales().netSales()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.sales().averageTicket()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.fiscal().pending()).isZero();
        assertThat(result.payments()).isEmpty();
        assertThat(result.recentSales()).isEmpty();
        assertThat(result.dailyPerformance()).hasSize(7);
    }
}