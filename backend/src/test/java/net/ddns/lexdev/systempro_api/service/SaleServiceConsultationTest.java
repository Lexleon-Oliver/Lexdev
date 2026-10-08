package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
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
class SaleServiceConsultationTest {

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
            fiscalProperties
        );
    }

    @Test
    void deveAplicarAutorizacaoObtidaPorConsulta() {
        Fixture fixture = fixture(1L);
        Instant issuedAt = Instant.parse("2026-10-07T20:00:00Z");
        NfceIssueResult result = new NfceIssueResult(
            FiscalDocumentStatus.AUTORIZADA,
            "2".repeat(44),
            "<NFe-assinada/>",
            "<retConsSitNFe cStat=\"100\"/>",
            "131260000000001",
            null,
            "Autorizado o uso da NF-e",
            issuedAt
        );
        when(gateway.consult(fixture.establishment(), fixture.document())).thenReturn(result);

        service.consult(fixture.saleId());

        verify(fixture.document()).setStatus(FiscalDocumentStatus.AUTORIZADA);
        verify(fixture.document()).setAccessKey("2".repeat(44));
        verify(fixture.document()).setXml("<NFe-assinada/>");
        verify(fixture.document()).setResponseXml("<retConsSitNFe cStat=\"100\"/>");
        verify(fixture.document()).setProtocol("131260000000001");
        verify(fixture.document()).setReceiptNumber(null);
        verify(fixture.document()).setReason("Autorizado o uso da NF-e");
        verify(fixture.document()).setIssuedAt(issuedAt);
        verify(fixture.sale()).setStatus(SaleStatus.FISCALIZADA);
        verify(gateway, never()).authorize(fixture.establishment(), fixture.sale(), fixture.document());
    }

    @Test
    void deveAplicarRejeicaoDefinitivaObtidaPorConsulta() {
        Fixture fixture = fixture(2L);
        NfceIssueResult result = new NfceIssueResult(
            FiscalDocumentStatus.REJEITADA,
            "3".repeat(44),
            "<NFe-assinada/>",
            "<retConsSitNFe cStat=\"301\"/>",
            null,
            null,
            "Uso Denegado",
            null
        );
        when(gateway.consult(fixture.establishment(), fixture.document())).thenReturn(result);

        service.consult(fixture.saleId());

        verify(fixture.document()).setStatus(FiscalDocumentStatus.REJEITADA);
        verify(fixture.sale()).setStatus(SaleStatus.FISCAL_REJEITADA);
        verify(gateway, never()).authorize(fixture.establishment(), fixture.sale(), fixture.document());
    }

    @Test
    void deveManterPendenciaQuandoConsultaContinuaInconclusiva() {
        Fixture fixture = fixture(3L);
        NfceIssueResult result = new NfceIssueResult(
            FiscalDocumentStatus.PENDENTE_CONSULTA,
            "4".repeat(44),
            "<NFe-assinada/>",
            null,
            null,
            "123456789012345",
            "Consulta ainda sem resultado fiscal definitivo",
            null
        );
        when(gateway.consult(fixture.establishment(), fixture.document())).thenReturn(result);

        service.consult(fixture.saleId());

        verify(fixture.document()).setStatus(FiscalDocumentStatus.PENDENTE_CONSULTA);
        verify(fixture.document()).setReceiptNumber("123456789012345");
        verify(fixture.sale()).setStatus(SaleStatus.FISCAL_PENDENTE);
        verify(gateway, never()).authorize(fixture.establishment(), fixture.sale(), fixture.document());
    }

    @Test
    void devePropagarFalhaLocalDaConsultaSemMascararComoPendencia() {
        Fixture fixture = fixture(4L);
        when(gateway.consult(fixture.establishment(), fixture.document()))
            .thenThrow(new FiscalIntegrationException("Certificado A1/TLS inválido"));

        assertThatThrownBy(() -> service.consult(fixture.saleId()))
            .isInstanceOf(FiscalIntegrationException.class)
            .hasMessageContaining("Certificado A1/TLS inválido");

        verify(fixture.document(), never()).setStatus(FiscalDocumentStatus.PENDENTE_CONSULTA);
        verify(fixture.sale(), never()).setStatus(SaleStatus.FISCAL_PENDENTE);
    }

    private Fixture fixture(long saleId) {
        Sale sale = mock(Sale.class);
        FiscalDocument document = mock(FiscalDocument.class);
        FiscalEstablishment establishment = mock(FiscalEstablishment.class);
        User user = mock(User.class);

        when(saleRepository.findForFiscal(saleId)).thenReturn(Optional.of(sale));
        when(fiscalDocumentRepository.findBySaleId(saleId)).thenReturn(Optional.of(document));
        when(document.getEstablishment()).thenReturn(establishment);
        lenient().when(sale.getFiscalEstablishment()).thenReturn(establishment);
        lenient().when(sale.getUser()).thenReturn(user);
        lenient().when(sale.getItems()).thenReturn(List.of());
        lenient().when(sale.getPayments()).thenReturn(List.of());
        lenient().when(sale.getSubtotal()).thenReturn(BigDecimal.ZERO);
        lenient().when(sale.getDiscount()).thenReturn(BigDecimal.ZERO);
        lenient().when(sale.getTotal()).thenReturn(BigDecimal.ZERO);

        return new Fixture(saleId, sale, document, establishment);
    }

    private record Fixture(
        long saleId,
        Sale sale,
        FiscalDocument document,
        FiscalEstablishment establishment
    ) {}
}
