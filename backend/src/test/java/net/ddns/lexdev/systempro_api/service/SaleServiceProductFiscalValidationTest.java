package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
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

import net.ddns.lexdev.systempro_api.config.FiscalProperties;
import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.FiscalProductProfile;
import net.ddns.lexdev.systempro_api.domain.Product;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.dto.SaleCreateRequestDto;
import net.ddns.lexdev.systempro_api.dto.SaleItemRequestDto;
import net.ddns.lexdev.systempro_api.dto.SalePaymentRequestDto;
import net.ddns.lexdev.systempro_api.enums.PaymentMethod;
import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;
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
            null, null, "application/x-pkcs12", false
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