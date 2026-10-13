package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import net.ddns.lexdev.systempro_api.config.FiscalProperties;
import net.ddns.lexdev.systempro_api.dto.SaleSummaryResponseDto;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.FiscalEmissionType;
import net.ddns.lexdev.systempro_api.enums.SaleStatus;
import net.ddns.lexdev.systempro_api.fiscal.SefazNfceGateway;
import net.ddns.lexdev.systempro_api.fiscal.contingency.NfceContingencyPolicy;
import net.ddns.lexdev.systempro_api.repository.ClientRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalDocumentRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalEventRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalEstablishmentRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalProductProfileRepository;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.repository.SaleRepository;
import net.ddns.lexdev.systempro_api.repository.projection.SaleSummaryProjection;

class SaleServiceFiscalPendingTest {

    @Test
    void deveBuscarSomenteEstadosFiscaisPendentesMantendoPaginacaoEOrdenacao() {
        SaleRepository saleRepository = mock(SaleRepository.class);
        FiscalDocumentRepository fiscalDocumentRepository = mock(FiscalDocumentRepository.class);
        SaleService service = service(saleRepository, fiscalDocumentRepository);

        Pageable pageable = PageRequest.of(1, 20, Sort.by(Sort.Direction.ASC, "saleAt"));
        SaleSummaryProjection projection = mock(SaleSummaryProjection.class);
        when(projection.getId()).thenReturn(42L);
        when(projection.getStatus()).thenReturn(SaleStatus.FISCAL_PENDENTE);
        when(projection.getSaleAt()).thenReturn(Instant.parse("2026-10-08T18:00:00Z"));
        when(projection.getTotal()).thenReturn(new BigDecimal("10.0000"));
        when(projection.getFiscalDocumentId()).thenReturn(77L);
        when(projection.getFiscalNumber()).thenReturn(123L);
        when(projection.getFiscalEmissionType()).thenReturn(FiscalEmissionType.NORMAL);
        when(projection.getFiscalStatus()).thenReturn(FiscalDocumentStatus.AGUARDANDO_AUTORIZACAO);
        Page<SaleSummaryProjection> repositoryPage = new PageImpl<>(List.of(projection), pageable, 41);

        when(saleRepository.findPendingSummaries(any(), any())).thenReturn(repositoryPage);

        Page<SaleSummaryResponseDto> result = service.findFiscalPending(pageable);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<FiscalDocumentStatus>> statusesCaptor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(saleRepository).findPendingSummaries(statusesCaptor.capture(), pageableCaptor.capture());

        assertThat(statusesCaptor.getValue()).containsExactlyInAnyOrder(
            FiscalDocumentStatus.AGUARDANDO_AUTORIZACAO,
            FiscalDocumentStatus.PENDENTE_CONSULTA,
            FiscalDocumentStatus.CONTINGENCIA,
            FiscalDocumentStatus.CANCELAMENTO_PENDENTE
        );
        assertThat(statusesCaptor.getValue()).doesNotContain(
            FiscalDocumentStatus.AUTORIZADA,
            FiscalDocumentStatus.REJEITADA,
            FiscalDocumentStatus.CANCELADA
        );
        assertThat(pageableCaptor.getValue()).isEqualTo(pageable);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotalElements()).isEqualTo(41);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(42L);
        assertThat(result.getContent().getFirst().total()).isEqualByComparingTo("10.0000");
        assertThat(result.getContent().getFirst().fiscalDocument().number()).isEqualTo(123L);
        assertThat(result.getContent().getFirst().fiscalDocument().status())
            .isEqualTo(FiscalDocumentStatus.AGUARDANDO_AUTORIZACAO);
        verifyNoInteractions(fiscalDocumentRepository);
    }

    private static SaleService service(
        SaleRepository saleRepository,
        FiscalDocumentRepository fiscalDocumentRepository
    ) {
        return new SaleService(
            saleRepository,
            mock(ProductRepository.class),
            mock(FiscalProductProfileRepository.class),
            mock(FiscalEstablishmentRepository.class),
            mock(ClientRepository.class),
            fiscalDocumentRepository,
            mock(FiscalEventRepository.class),
            mock(CurrentUserProvider.class),
            mock(FiscalEstablishmentService.class),
            mock(SefazNfceGateway.class),
            mock(FiscalProperties.class),
            mock(IbsCbsSaleSnapshotService.class),
            mock(NfceContingencyPolicy.class),
            mock(StockService.class)
        );
    }

}