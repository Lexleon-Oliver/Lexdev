package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
import net.ddns.lexdev.systempro_api.domain.FiscalEvent;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.FiscalEventType;
import net.ddns.lexdev.systempro_api.enums.SaleStatus;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;
import net.ddns.lexdev.systempro_api.fiscal.NfceIssueResult;
import net.ddns.lexdev.systempro_api.fiscal.SefazNfceGateway;
import net.ddns.lexdev.systempro_api.repository.ClientRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalDocumentRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalEstablishmentRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalEventRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalProductProfileRepository;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.repository.SaleRepository;

@ExtendWith(MockitoExtension.class)
class SaleServiceCancellationTest {

    private static final String ACCESS_KEY = "3".repeat(44);
    private static final String AUTH_PROTOCOL = "131260000000001";
    private static final String JUSTIFICATION = "Cancelamento solicitado pelo operador";

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
    @Mock private FiscalProperties fiscalProperties;

    private SaleService service;

    @BeforeEach
    void setUp() {
        service = new SaleService(
            saleRepository, productRepository, profileRepository, establishmentRepository,
            clientRepository, fiscalDocumentRepository, fiscalEventRepository,
            currentUserProvider, fiscalEstablishmentService, gateway, fiscalProperties
        );
    }

    @Test
    void deveAplicarCancelamentoConfirmadoEPersistirEvento() {
        Fixture fixture = fixture(1L);
        NfceIssueResult result = new NfceIssueResult(
            FiscalDocumentStatus.CANCELADA,
            ACCESS_KEY,
            "<envEvento-assinado/>",
            "<retEvento cStat=\"135\"/>",
            "131260000000999",
            null,
            "Evento registrado e vinculado a NF-e",
            null
        );
        when(gateway.cancel(fixture.establishment(), fixture.document(), JUSTIFICATION)).thenReturn(result);
        when(fiscalEventRepository.findMaxSequenceNumber(1L, FiscalEventType.CANCELAMENTO)).thenReturn(0);

        service.cancel(fixture.saleId(), JUSTIFICATION);

        verify(fixture.document()).setStatus(FiscalDocumentStatus.CANCELADA);
        verify(fixture.document()).setResponseXml("<retEvento cStat=\"135\"/>");
        verify(fixture.document()).setCancellationProtocol("131260000000999");
        verify(fixture.document()).setCanceledAt(any());
        verify(fixture.sale()).setStatus(SaleStatus.CANCELADA);
        verify(fiscalEventRepository).save(any(FiscalEvent.class));
    }

    @Test
    void deveManterCancelamentoPendenteQuandoTransmissaoForIndeterminada() {
        Fixture fixture = fixture(2L);
        NfceIssueResult result = new NfceIssueResult(
            FiscalDocumentStatus.CANCELAMENTO_PENDENTE,
            ACCESS_KEY,
            "<envEvento-assinado/>",
            null,
            null,
            null,
            "Resultado da transmissão do cancelamento é indeterminado",
            null
        );
        when(gateway.cancel(fixture.establishment(), fixture.document(), JUSTIFICATION)).thenReturn(result);
        when(fiscalEventRepository.findMaxSequenceNumber(2L, FiscalEventType.CANCELAMENTO)).thenReturn(0);

        service.cancel(fixture.saleId(), JUSTIFICATION);

        verify(fixture.document()).setStatus(FiscalDocumentStatus.CANCELAMENTO_PENDENTE);
        verify(fixture.document(), never()).setCanceledAt(any());
        verify(fixture.sale(), never()).setStatus(SaleStatus.CANCELADA);
        verify(fiscalEventRepository).save(any(FiscalEvent.class));
    }

    @Test
    void devePropagarFalhaLocalSemFabricarCancelamentoPendenteNemEvento() {
        Fixture fixture = fixture(3L);
        when(gateway.cancel(fixture.establishment(), fixture.document(), JUSTIFICATION))
            .thenThrow(new FiscalIntegrationException("Certificado A1/TLS inválido"));

        assertThatThrownBy(() -> service.cancel(fixture.saleId(), JUSTIFICATION))
            .isInstanceOf(FiscalIntegrationException.class)
            .hasMessageContaining("Certificado A1/TLS inválido");

        verify(fixture.document(), never()).setStatus(FiscalDocumentStatus.CANCELAMENTO_PENDENTE);
        verify(fixture.document(), never()).setReason(any());
        verify(fiscalEventRepository, never()).save(any(FiscalEvent.class));
        verify(fixture.sale(), never()).setStatus(SaleStatus.CANCELADA);
    }

    @Test
    void deveRecusarCancelamentoQuandoDocumentoNaoEstaAutorizado() {
        Fixture fixture = fixture(4L, FiscalDocumentStatus.PENDENTE_CONSULTA);

        assertThatThrownBy(() -> service.cancel(fixture.saleId(), JUSTIFICATION))
            .hasMessageContaining("Somente uma NFC-e autorizada pode ser cancelada");

        verify(gateway, never()).cancel(any(), any(), any());
        verify(fiscalEventRepository, never()).save(any(FiscalEvent.class));
    }

    private Fixture fixture(long saleId) {
        return fixture(saleId, FiscalDocumentStatus.AUTORIZADA);
    }

    private Fixture fixture(long saleId, FiscalDocumentStatus status) {
        Sale sale = mock(Sale.class);
        FiscalDocument document = mock(FiscalDocument.class);
        FiscalEstablishment establishment = mock(FiscalEstablishment.class);
        User user = mock(User.class);

        when(saleRepository.findForFiscal(saleId)).thenReturn(Optional.of(sale));
        when(fiscalDocumentRepository.findBySaleId(saleId)).thenReturn(Optional.of(document));
        when(document.getStatus()).thenReturn(status);
        lenient().when(document.getId()).thenReturn(saleId);
        lenient().when(document.getAccessKey()).thenReturn(ACCESS_KEY);
        lenient().when(document.getProtocol()).thenReturn(AUTH_PROTOCOL);
        lenient().when(document.getEstablishment()).thenReturn(establishment);
        lenient().when(sale.getFiscalEstablishment()).thenReturn(establishment);
        lenient().when(sale.getUser()).thenReturn(user);
        lenient().when(sale.getItems()).thenReturn(List.of());
        lenient().when(sale.getPayments()).thenReturn(List.of());
        lenient().when(sale.getSubtotal()).thenReturn(BigDecimal.ZERO);
        lenient().when(sale.getDiscount()).thenReturn(BigDecimal.ZERO);
        lenient().when(sale.getTotal()).thenReturn(BigDecimal.ZERO);

        return new Fixture(saleId, sale, document, establishment);
    }

    private record Fixture(long saleId, Sale sale, FiscalDocument document, FiscalEstablishment establishment) {}
}
