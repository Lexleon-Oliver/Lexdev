package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.ddns.lexdev.systempro_api.config.FiscalProperties;
import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.FiscalEmissionType;
import net.ddns.lexdev.systempro_api.enums.SaleStatus;
import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;
import net.ddns.lexdev.systempro_api.fiscal.NfceIssueResult;
import net.ddns.lexdev.systempro_api.fiscal.SefazNfceGateway;
import net.ddns.lexdev.systempro_api.fiscal.contingency.NfceContingencyPolicy;
import net.ddns.lexdev.systempro_api.repository.ClientRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalDocumentRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalEstablishmentRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalEventRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalProductProfileRepository;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.repository.SaleRepository;
import net.ddns.lexdev.systempro_api.service.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class SaleServiceFiscalFailureTest {

    @Mock private SaleRepository saleRepository;
    @Mock private ProductRepository productRepository;
    @Mock private FiscalProductProfileRepository profileRepository;
    @Mock private FiscalEstablishmentRepository establishmentRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private FiscalDocumentRepository fiscalDocumentRepository;
    @Mock private FiscalEventRepository fiscalEventRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private FiscalEstablishmentService fiscalEstablishmentService;
    @Mock private SefazNfceGateway gateway;
    @Mock private IbsCbsSaleSnapshotService rtcSnapshotService;
    @Mock private FiscalProperties fiscalProperties;

    @Mock private NfceContingencyPolicy contingencyPolicy;

    private SaleService service;

    @BeforeEach
    void setUp() {
        when(fiscalProperties.enabled()).thenReturn(true);
        service = new SaleService(
            saleRepository,
            productRepository,
            profileRepository,
            establishmentRepository,
            clientRepository,
            fiscalDocumentRepository,
            fiscalEventRepository,
            currentUserProvider,
            fiscalEstablishmentService,
            gateway,
            fiscalProperties,
            rtcSnapshotService,
            contingencyPolicy
        );
    }

    @Test
    void deveBloquearSomenteAEmissaoQuandoFiscalEstiverDesabilitado() {
        when(fiscalProperties.enabled()).thenReturn(false);

        assertThatThrownBy(() -> service.issue(99L))
            .isInstanceOf(FiscalConfigurationException.class)
            .hasMessageContaining("emissão fiscal está desabilitada");

        verify(saleRepository, never()).findForFiscal(99L);
    }

    @Test
    void devePropagarFalhaLocalSemMarcarDocumentoComoPendenteConsulta() {
        long saleId = 1L;
        Sale sale = mock(Sale.class);
        FiscalDocument document = mock(FiscalDocument.class);
        FiscalEstablishment establishmentRef = mock(FiscalEstablishment.class);
        FiscalEstablishment establishment = mock(FiscalEstablishment.class);

        when(establishmentRef.getId()).thenReturn(10L);
        when(document.getEstablishment()).thenReturn(establishmentRef);
        when(document.getStatus()).thenReturn(FiscalDocumentStatus.AGUARDANDO_AUTORIZACAO);
        when(saleRepository.findForFiscal(saleId)).thenReturn(Optional.of(sale));
        when(fiscalDocumentRepository.findBySaleId(saleId)).thenReturn(Optional.of(document));
        when(fiscalEstablishmentService.requireDetailed(10L)).thenReturn(establishment);
        when(gateway.authorize(establishment, sale, document))
            .thenThrow(new FiscalIntegrationException("XML/assinatura/certificado inválido"));

        assertThatThrownBy(() -> service.issue(saleId))
            .isInstanceOf(FiscalIntegrationException.class)
            .hasMessageContaining("XML/assinatura/certificado inválido");

        verify(document, never()).setStatus(FiscalDocumentStatus.PENDENTE_CONSULTA);
        verify(sale, never()).setStatus(SaleStatus.FISCAL_PENDENTE);
    }

    @Test
    void devePersistirEstadoPendenteQuandoGatewayInformaResultadoIndeterminado() {
        long saleId = 2L;
        Sale sale = mock(Sale.class);
        FiscalDocument document = mock(FiscalDocument.class);
        FiscalEstablishment establishmentRef = mock(FiscalEstablishment.class);
        FiscalEstablishment establishment = mock(FiscalEstablishment.class);
        User user = mock(User.class);

        when(establishmentRef.getId()).thenReturn(20L);
        when(document.getEstablishment()).thenReturn(establishmentRef);
        when(document.getStatus()).thenReturn(FiscalDocumentStatus.AGUARDANDO_AUTORIZACAO);
        when(saleRepository.findForFiscal(saleId)).thenReturn(Optional.of(sale));
        when(fiscalDocumentRepository.findBySaleId(saleId)).thenReturn(Optional.of(document));
        when(fiscalEstablishmentService.requireDetailed(20L)).thenReturn(establishment);
        when(sale.getFiscalEstablishment()).thenReturn(establishmentRef);
        when(sale.getUser()).thenReturn(user);
        when(sale.getItems()).thenReturn(List.of());
        when(sale.getPayments()).thenReturn(List.of());
        when(sale.getSubtotal()).thenReturn(BigDecimal.ZERO);
        when(sale.getDiscount()).thenReturn(BigDecimal.ZERO);
        when(sale.getTotal()).thenReturn(BigDecimal.ZERO);

        NfceIssueResult pending = new NfceIssueResult(
            FiscalDocumentStatus.PENDENTE_CONSULTA,
            "1".repeat(44),
            "<NFe/>",
            null,
            null,
            null,
            "resultado da transmissão é indeterminado",
            null
        );
        when(gateway.authorize(establishment, sale, document)).thenReturn(pending);

        service.issue(saleId);

        verify(document).setStatus(FiscalDocumentStatus.PENDENTE_CONSULTA);
        verify(document).setAccessKey("1".repeat(44));
        verify(document).setXml("<NFe/>");
        verify(document).setReason("resultado da transmissão é indeterminado");
        verify(sale).setStatus(SaleStatus.FISCAL_PENDENTE);
    }
    @Test
    void naoDeveReemitirComoNormalDocumentoJaPreparadoEmContingencia() {
        long saleId = 3L;
        Sale sale = mock(Sale.class);
        FiscalDocument document = mock(FiscalDocument.class);
        FiscalEstablishment establishmentRef = mock(FiscalEstablishment.class);
        FiscalEstablishment establishment = mock(FiscalEstablishment.class);

        when(establishmentRef.getId()).thenReturn(30L);
        when(document.getEstablishment()).thenReturn(establishmentRef);
        when(document.getStatus()).thenReturn(FiscalDocumentStatus.CONTINGENCIA);
        when(saleRepository.findForFiscal(saleId)).thenReturn(Optional.of(sale));
        when(fiscalDocumentRepository.findBySaleId(saleId)).thenReturn(Optional.of(document));
        when(fiscalEstablishmentService.requireDetailed(30L)).thenReturn(establishment);

        assertThatThrownBy(() -> service.issue(saleId))
            .isInstanceOf(net.ddns.lexdev.systempro_api.exception.BusinessException.class)
            .hasMessageContaining("transmissão da contingência");

        verify(gateway, never()).authorize(establishment, sale, document);
    }

    @Test
    void transmissaoDaContingenciaDeveUsarDocumentoPersistidoSemNovaAutorizacaoNormal() {
        long saleId = 4L;
        Sale sale = mock(Sale.class);
        FiscalDocument document = mock(FiscalDocument.class);
        FiscalEstablishment establishmentRef = mock(FiscalEstablishment.class);
        FiscalEstablishment establishment = mock(FiscalEstablishment.class);

        when(establishmentRef.getId()).thenReturn(40L);
        when(document.getEstablishment()).thenReturn(establishmentRef);
        when(saleRepository.findForFiscal(saleId)).thenReturn(Optional.of(sale));
        when(fiscalDocumentRepository.findBySaleId(saleId)).thenReturn(Optional.of(document));
        when(fiscalEstablishmentService.requireDetailed(40L)).thenReturn(establishment);
        when(gateway.transmitOfflineContingency(establishment, document))
            .thenThrow(new FiscalIntegrationException("falha controlada após validar roteamento"));

        assertThatThrownBy(() -> service.transmitOfflineContingency(saleId))
            .isInstanceOf(FiscalIntegrationException.class)
            .hasMessageContaining("falha controlada");

        verify(contingencyPolicy).assertCanTransmitOfflineContingency(document);
        verify(gateway).transmitOfflineContingency(establishment, document);
        verify(gateway, never()).authorize(establishment, sale, document);
    }

}