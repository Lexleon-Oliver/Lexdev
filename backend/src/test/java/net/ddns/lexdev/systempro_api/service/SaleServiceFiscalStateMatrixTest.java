package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.ddns.lexdev.systempro_api.config.FiscalProperties;
import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.fiscal.SefazNfceGateway;
import net.ddns.lexdev.systempro_api.fiscal.contingency.NfceContingencyPolicy;
import net.ddns.lexdev.systempro_api.repository.ClientRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalDocumentRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalEstablishmentRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalEventRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalProductProfileRepository;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.repository.SaleRepository;

@ExtendWith(MockitoExtension.class)
class SaleServiceFiscalStateMatrixTest {

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
        service = new SaleService(
            saleRepository, productRepository, profileRepository, establishmentRepository,
            clientRepository, fiscalDocumentRepository, fiscalEventRepository, currentUserProvider,
            fiscalEstablishmentService, gateway, fiscalProperties, rtcSnapshotService, contingencyPolicy
        );
    }

    @ParameterizedTest(name = "issue bloqueado em {0}")
    @MethodSource("estadosQueNaoPermitemEmissaoNormal")
    void emissaoNormalNaoDeveAtravessarGatewayEmEstadoInvalido(FiscalDocumentStatus status) {
        long saleId = 100L + status.ordinal();
        Fixture fixture = fixture(saleId, status);
        when(fiscalProperties.enabled()).thenReturn(true);

        assertThatThrownBy(() -> service.issue(saleId))
            .isInstanceOf(BusinessException.class);

        verify(gateway, never()).authorize(any(), any(), any());
        verify(fiscalEstablishmentService, never()).assertReadyForEmission(any());
    }

    @ParameterizedTest(name = "consulta comum bloqueada em {0}")
    @MethodSource("estadosExcetoPendenteConsulta")
    void consultaComumNaoDeveAtravessarGatewayForaDePendenteConsulta(FiscalDocumentStatus status) {
        long saleId = 200L + status.ordinal();
        Fixture fixture = fixture(saleId, status);

        assertThatThrownBy(() -> service.consult(saleId))
            .isInstanceOf(BusinessException.class);

        verify(gateway, never()).consult(any(), any());
        verify(gateway, never()).consultCancellation(any(), any());
    }

    @ParameterizedTest(name = "cancelamento bloqueado em {0}")
    @MethodSource("estadosExcetoAutorizada")
    void cancelamentoNaoDeveAtravessarGatewayForaDeAutorizada(FiscalDocumentStatus status) {
        long saleId = 300L + status.ordinal();
        Fixture fixture = fixture(saleId, status);

        assertThatThrownBy(() -> service.cancel(saleId, "Cancelamento fiscal de teste"))
            .isInstanceOf(BusinessException.class);

        verify(gateway, never()).cancel(any(), any(), any());
    }

    @ParameterizedTest(name = "consulta de cancelamento bloqueada em {0}")
    @MethodSource("estadosInvalidosParaConsultaCancelamento")
    void consultaCancelamentoNaoDeveAtravessarGatewayForaDeCancelamentoPendente(FiscalDocumentStatus status) {
        long saleId = 400L + status.ordinal();
        Fixture fixture = fixture(saleId, status);

        assertThatThrownBy(() -> service.consultPendingCancellation(saleId))
            .isInstanceOf(BusinessException.class);

        verify(gateway, never()).consultCancellation(any(), any());
        verify(gateway, never()).cancel(any(), any(), any());
    }

    private Fixture fixture(long saleId, FiscalDocumentStatus status) {
        Sale sale = mock(Sale.class);
        FiscalDocument document = mock(FiscalDocument.class);
        when(saleRepository.findForFiscal(saleId)).thenReturn(Optional.of(sale));
        when(fiscalDocumentRepository.findBySaleId(saleId)).thenReturn(Optional.of(document));
        when(document.getStatus()).thenReturn(status);
        return new Fixture(sale, document);
    }

    static Stream<FiscalDocumentStatus> estadosQueNaoPermitemEmissaoNormal() {
        return Stream.of(
            FiscalDocumentStatus.PENDENTE_CONSULTA,
            FiscalDocumentStatus.REJEITADA,
            FiscalDocumentStatus.CANCELAMENTO_PENDENTE,
            FiscalDocumentStatus.CANCELADA,
            FiscalDocumentStatus.CONTINGENCIA
        );
    }

    static Stream<FiscalDocumentStatus> estadosExcetoPendenteConsulta() {
        return Stream.of(FiscalDocumentStatus.values())
            .filter(status -> status != FiscalDocumentStatus.PENDENTE_CONSULTA);
    }

    static Stream<FiscalDocumentStatus> estadosExcetoAutorizada() {
        return Stream.of(FiscalDocumentStatus.values())
            .filter(status -> status != FiscalDocumentStatus.AUTORIZADA);
    }


    static Stream<FiscalDocumentStatus> estadosInvalidosParaConsultaCancelamento() {
        return Stream.of(FiscalDocumentStatus.values())
            .filter(status -> status != FiscalDocumentStatus.CANCELAMENTO_PENDENTE)
            .filter(status -> status != FiscalDocumentStatus.CANCELADA);
    }

    private record Fixture(Sale sale, FiscalDocument document) {}
}