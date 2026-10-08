package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import net.ddns.lexdev.systempro_api.config.FiscalProperties;
import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.User;
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

class SaleServiceFiscalPendingTest {

    @Test
    void deveBuscarSomenteEstadosFiscaisPendentesMantendoPaginacaoEOrdenacao() {
        SaleRepository saleRepository = mock(SaleRepository.class);
        FiscalDocumentRepository fiscalDocumentRepository = mock(FiscalDocumentRepository.class);
        SaleService service = service(saleRepository, fiscalDocumentRepository);

        Pageable pageable = PageRequest.of(1, 20, Sort.by(Sort.Direction.ASC, "saleAt"));
        Sale sale = sale(42L);
        FiscalDocument document = fiscalDocument();
        Page<Sale> repositoryPage = new PageImpl<>(List.of(sale), pageable, 41);

        when(saleRepository.findByFiscalDocumentStatusIn(any(), any())).thenReturn(repositoryPage);
        when(fiscalDocumentRepository.findBySaleId(42L)).thenReturn(Optional.of(document));

        Page<?> result = service.findFiscalPending(pageable);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<FiscalDocumentStatus>> statusesCaptor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(saleRepository).findByFiscalDocumentStatusIn(statusesCaptor.capture(), pageableCaptor.capture());

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
            mock(NfceContingencyPolicy.class)
        );
    }

    private static Sale sale(Long id) {
        Sale sale = mock(Sale.class);
        FiscalEstablishment establishment = mock(FiscalEstablishment.class);
        User user = mock(User.class);

        when(sale.getId()).thenReturn(id);
        when(sale.getFiscalEstablishment()).thenReturn(establishment);
        when(establishment.getId()).thenReturn(7L);
        when(sale.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(9L);
        when(sale.getStatus()).thenReturn(SaleStatus.FISCAL_PENDENTE);
        when(sale.getSaleAt()).thenReturn(Instant.parse("2026-10-08T18:00:00Z"));
        when(sale.getSubtotal()).thenReturn(new BigDecimal("10.0000"));
        when(sale.getDiscount()).thenReturn(BigDecimal.ZERO.setScale(4));
        when(sale.getTotal()).thenReturn(new BigDecimal("10.0000"));
        when(sale.getItems()).thenReturn(List.of());
        when(sale.getPayments()).thenReturn(List.of());
        return sale;
    }

    private static FiscalDocument fiscalDocument() {
        FiscalDocument document = mock(FiscalDocument.class);
        when(document.getModel()).thenReturn("65");
        when(document.getSeries()).thenReturn(1);
        when(document.getNumber()).thenReturn(1L);
        when(document.getEmissionType()).thenReturn(FiscalEmissionType.NORMAL);
        when(document.getStatus()).thenReturn(FiscalDocumentStatus.AGUARDANDO_AUTORIZACAO);
        return document;
    }
}