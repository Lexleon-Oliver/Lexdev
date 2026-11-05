package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
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
import org.mockito.ArgumentCaptor;

import net.ddns.lexdev.systempro_api.config.FiscalProperties;
import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.FiscalProductProfile;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.domain.Product;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.dto.SaleCreateRequestDto;
import net.ddns.lexdev.systempro_api.dto.SaleItemRequestDto;
import net.ddns.lexdev.systempro_api.dto.SalePaymentRequestDto;
import net.ddns.lexdev.systempro_api.enums.PaymentMethod;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;
import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsCalculation;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsJurisdictionCalculation;
import net.ddns.lexdev.systempro_api.fiscal.SefazNfceGateway;
import net.ddns.lexdev.systempro_api.fiscal.contingency.NfceContingencyPolicy;
import net.ddns.lexdev.systempro_api.repository.ClientRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalDocumentRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalEventRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalEstablishmentRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalProductProfileRepository;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.repository.SaleRepository;

@ExtendWith(MockitoExtension.class)
class SaleServiceProductFiscalValidationTest {

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

    @Mock private NfceContingencyPolicy contingencyPolicy;

    @Mock private StockService stockService;

    private SaleService service;
    private FiscalEstablishment establishment;
    private Product product;
    private FiscalProductProfile profile;

    @BeforeEach
    void setUp() {
        FiscalProperties properties = new FiscalProperties(
            true, 10, 30, "test-key", "4.00", "SystemPro-Test",
            null, null, "application/x-pkcs12", false, false
        );

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
            properties,
            rtcSnapshotService,
            contingencyPolicy,
            stockService
        );

        establishment = new FiscalEstablishment(null);
        establishment.setSeries(1);
        establishment.setNextNumber(900L);

        product = validProduct();
        profile = validProfile(product);

        lenient().when(establishmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(establishment));
        lenient().doNothing().when(fiscalEstablishmentService).assertReadyForEmission(establishment);
        lenient().when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        lenient().when(profileRepository.findByProductId(product.getId())).thenReturn(Optional.of(profile));
        lenient().when(currentUserProvider.requireUser()).thenReturn(
            new User("tester", "Tester", "tester@example.com", "secret", "ROLE_USER")
        );
        lenient().when(saleRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(fiscalDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldRejectProductWithoutEightDigitNcmBeforePersistingSaleOrConsumingNumber() {
        product.setNcm("1234");

        assertFiscalRejection("precisa ter NCM com 8 dígitos");
    }

    @Test
    void shouldRejectInvalidFiscalOriginBeforePersistingSaleOrConsumingNumber() {
        product.setOrigin("9");

        assertFiscalRejection("precisa ter origem fiscal válida");
    }

    @Test
    void shouldRejectMalformedGtinBeforePersistingSaleOrConsumingNumber() {
        product.setGtin("123456789");

        assertFiscalRejection("GTIN do produto P001 é inválido");
    }

    @Test
    void shouldRejectIncompleteFiscalProfileBeforePersistingSaleOrConsumingNumber() {
        profile.setCfop("510");

        assertFiscalRejection("perfil fiscal do produto P001 está incompleto ou possui códigos inválidos");
    }

    @Test
    void shouldRejectUnsupportedIcmsRuleBeforePersistingSaleOrConsumingNumber() {
        profile.setIcmsCstCsosn("500");

        assertFiscalRejection("CST/CSOSN 500 ainda não possui regra fiscal parametrizada");
    }

    @Test
    void shouldRejectUnsupportedPisAndCofinsRulesBeforePersistingSaleOrConsumingNumber() {
        profile.setPisCst("99");

        assertFiscalRejection("CST de PIS 99 ainda não possui regra fiscal parametrizada");

        profile.setPisCst("07");
        profile.setCofinsCst("99");

        assertFiscalRejection("CST de COFINS 99 ainda não possui regra fiscal parametrizada");
    }

    @Test
    void shouldRequireRatesForTaxablePisAndCofinsBeforePersistingSaleOrConsumingNumber() {
        profile.setPisCst("01");
        profile.setPisRate(null);

        assertFiscalRejection("Informe a alíquota de PIS para o CST 01");

        profile.setPisCst("07");
        profile.setCofinsCst("01");
        profile.setCofinsRate(null);

        assertFiscalRejection("Informe a alíquota de COFINS para o CST 01");
    }

    @Test
    void shouldRejectLegacyRtcRatesBeforePersistingSaleOrConsumingNumber() {
        profile.setIbsRate(new BigDecimal("0.1000"));

        assertFiscalRejection("alíquotas IBS/CBS legadas");
    }

    @Test
    void validSupportedFiscalProductShouldCreateDocumentAndConsumeExactlyOneNumber() {
        when(saleRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fiscalDocumentRepository.save(any(FiscalDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request());

        verify(saleRepository).saveAndFlush(any());
        verify(fiscalDocumentRepository).save(any(FiscalDocument.class));
        assertThat(establishment.getNextNumber()).isEqualTo(901L);
    }

    private void assertFiscalRejection(String expectedMessage) {
        assertThatThrownBy(() -> service.create(request()))
            .isInstanceOf(FiscalConfigurationException.class)
            .hasMessageContaining(expectedMessage);

        verify(saleRepository, never()).save(any());
        verify(fiscalDocumentRepository, never()).save(any());
        assertThat(establishment.getNextNumber()).isEqualTo(900L);
    }

    @Test
    void deveFecharDoisItensDe125gEm266Centavos() {
        product.setUnitOfMeasure("KG");
        product.setSalePrice(new BigDecimal("10.60"));

        var response = service.create(new SaleCreateRequestDto(
            1L, null, null,
            List.of(
                new SaleItemRequestDto(10L, new BigDecimal("0.125"), null, BigDecimal.ZERO),
                new SaleItemRequestDto(10L, new BigDecimal("0.125"), null, BigDecimal.ZERO)
            ),
            List.of(new SalePaymentRequestDto(PaymentMethod.DINHEIRO, new BigDecimal("2.66"), null, null)),
            BigDecimal.ZERO, null
        ));

        assertThat(response.subtotal()).isEqualByComparingTo("2.66");
        assertThat(response.total()).isEqualByComparingTo("2.66");
        assertThat(response.change()).isEqualByComparingTo("0.00");
        assertThat(response.items()).hasSize(2).allSatisfy(item -> {
            assertThat(item.total()).isEqualByComparingTo("1.33");
            assertThat(item.quantity()).isEqualByComparingTo("0.125");
        });
        verify(stockService).registerSaleOut(any());
    }

    @Test
    void deveDistribuirDescontoEmCentavosERespeitarTroco() {
        product.setUnitOfMeasure("KG");
        var response = service.create(new SaleCreateRequestDto(
            1L, null, null,
            List.of(
                new SaleItemRequestDto(10L, new BigDecimal("0.125"), new BigDecimal("10.60"), new BigDecimal("0.01")),
                new SaleItemRequestDto(10L, new BigDecimal("0.125"), new BigDecimal("10.60"), BigDecimal.ZERO)
            ),
            List.of(new SalePaymentRequestDto(PaymentMethod.DINHEIRO, new BigDecimal("2.65"), null, null)),
            new BigDecimal("0.01"), null
        ));
        assertThat(response.total()).isEqualByComparingTo("2.64");
        assertThat(response.change()).isEqualByComparingTo("0.01");
        assertThat(response.items().stream().map(net.ddns.lexdev.systempro_api.dto.SaleItemResponseDto::total)
            .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(response.total());
        assertThat(response.items().stream().map(net.ddns.lexdev.systempro_api.dto.SaleItemResponseDto::discount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("0.02");
    }

    @Test
    void deveRatearDescontoEmCemItensDeUmCentavoSemEstourarUltimoItem() {
        product.setSalePrice(new BigDecimal("0.01"));
        var items = java.util.stream.IntStream.range(0, 100)
            .mapToObj(index -> new SaleItemRequestDto(10L, BigDecimal.ONE, null, BigDecimal.ZERO))
            .toList();
        var response = service.create(new SaleCreateRequestDto(
            1L, null, null, items,
            List.of(new SalePaymentRequestDto(PaymentMethod.DINHEIRO, new BigDecimal("0.51"), null, null)),
            new BigDecimal("0.49"), null
        ));
        assertThat(response.total()).isEqualByComparingTo("0.51");
        assertThat(response.items().stream().map(net.ddns.lexdev.systempro_api.dto.SaleItemResponseDto::total)
            .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("0.51");
    }

    @Test
    void deveRecusarQuantidadeOuPagamentoIncompativelAntesDeBaixarEstoque() {
        product.setUnitOfMeasure("KG");
        var invalidQuantity = new SaleCreateRequestDto(
            1L, null, null,
            List.of(new SaleItemRequestDto(10L, new BigDecimal("0.0001"), new BigDecimal("40.00"), BigDecimal.ZERO)),
            List.of(new SalePaymentRequestDto(PaymentMethod.DINHEIRO, new BigDecimal("1.00"), null, null)),
            BigDecimal.ZERO, null
        );
        assertThatThrownBy(() -> service.create(invalidQuantity))
            .isInstanceOf(net.ddns.lexdev.systempro_api.exception.BusinessException.class);
        verify(stockService, never()).registerSaleOut(any());
    }

    @Test
    void optInDeveRejeitarOperacaoNaoCobertaAntesDeSalvarVenda() {
        enableZeroTaxProducer();
        establishment.setTaxRegime(TaxRegime.SIMPLES_NACIONAL);

        assertThatThrownBy(() -> service.create(request()))
            .isInstanceOf(FiscalIntegrationException.class)
            .hasMessageContaining("somente Regime Normal");
        verify(saleRepository, never()).saveAndFlush(any());
        verify(stockService, never()).registerSaleOut(any());
        assertThat(establishment.getNextNumber()).isEqualTo(900L);
    }

    @Test
    void optInDevePersistirSnapshotSomenteComTodosTributosZerados() {
        enableZeroTaxProducer();
        product.setNcm("10063021");
        establishment.setTaxRegime(TaxRegime.REGIME_NORMAL);
        profile.setIcmsCstCsosn("40");
        profile.setPisCst("06");
        profile.setCofinsCst("06");
        profile.setIcmsRate(BigDecimal.ZERO);
        profile.setPisRate(BigDecimal.ZERO);
        profile.setCofinsRate(BigDecimal.ZERO);

        doAnswer(invocation -> {
            SaleItem item = invocation.getArgument(2);
            item.setIbsCbsCalculation(new IbsCbsCalculation(
                "200", "200003", item.getTotal(),
                new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO),
                new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO),
                BigDecimal.ZERO,
                new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO)
            ));
            return null;
        }).when(rtcSnapshotService).apply(any(), any(), any(), any());

        service.create(request());

        ArgumentCaptor<Sale> captor = ArgumentCaptor.forClass(Sale.class);
        verify(saleRepository).saveAndFlush(captor.capture());
        SaleItem item = captor.getValue().getItems().get(0);
        assertThat(item.getNfceLiquidSnapshot().unitWithoutTaxes()).isEqualByComparingTo("10.00");
        assertThat(item.getNfceLiquidSnapshot().totalWithoutTaxes()).isEqualByComparingTo("10.00");
        assertThat(item.getNfceLiquidCalculationSource()).isEqualTo("NT2026.008:ZERO-TAX-200003-V2");
    }

    private void enableZeroTaxProducer() {
        FiscalProperties enabledProperties = new FiscalProperties(
            true, 10, 30, "test-key", "4.00", "SystemPro-Test",
            null, null, "application/x-pkcs12", false, true
        );
        service = new SaleService(
            saleRepository, productRepository, profileRepository, establishmentRepository,
            clientRepository, fiscalDocumentRepository, fiscalEventRepository,
            currentUserProvider, fiscalEstablishmentService, gateway, enabledProperties,
            rtcSnapshotService, contingencyPolicy, stockService
        );
    }

    private static SaleCreateRequestDto request() {
        return new SaleCreateRequestDto(
            1L,
            null,
            null,
            List.of(new SaleItemRequestDto(10L, BigDecimal.ONE, new BigDecimal("10.00"), BigDecimal.ZERO)),
            List.of(new SalePaymentRequestDto(PaymentMethod.DINHEIRO, new BigDecimal("10.00"), null, null)),
            BigDecimal.ZERO,
            null
        );
    }

    private static Product validProduct() {
        Product product = new Product();
        product.setCode("P001");
        product.setName("Produto fiscal de teste");
        product.setUnitOfMeasure("UN");
        product.setSalePrice(new BigDecimal("10.00"));
        product.setNcm("12345678");
        product.setOrigin("0");
        return product;
    }

    private static FiscalProductProfile validProfile(Product product) {
        FiscalProductProfile profile = new FiscalProductProfile(product);
        profile.setCfop("5102");
        profile.setIcmsCstCsosn("102");
        profile.setPisCst("07");
        profile.setCofinsCst("07");
        return profile;
    }
}