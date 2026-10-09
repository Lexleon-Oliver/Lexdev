package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.ddns.lexdev.systempro_api.config.FiscalProperties;
import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.FiscalProductProfile;
import net.ddns.lexdev.systempro_api.domain.Product;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.dto.SaleCreateRequestDto;
import net.ddns.lexdev.systempro_api.dto.SaleItemRequestDto;
import net.ddns.lexdev.systempro_api.dto.SalePaymentRequestDto;
import net.ddns.lexdev.systempro_api.enums.PaymentMethod;
import net.ddns.lexdev.systempro_api.repository.ClientRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalDocumentRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalEventRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalEstablishmentRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalProductProfileRepository;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.repository.SaleRepository;
import net.ddns.lexdev.systempro_api.fiscal.SefazNfceGateway;
import net.ddns.lexdev.systempro_api.fiscal.contingency.NfceContingencyPolicy;

@ExtendWith(MockitoExtension.class)
class SaleServiceNumberingTest {

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
        establishment.setSeries(3);
        establishment.setNextNumber(500L);

        Product product = validProduct();
        FiscalProductProfile profile = validProfile(product);
        User user = new User("tester", "Tester", "tester@example.com", "secret", "ROLE_USER");

        when(establishmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(establishment));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(profileRepository.findByProductId(product.getId())).thenReturn(Optional.of(profile));
        when(currentUserProvider.requireUser()).thenReturn(user);
        when(saleRepository.saveAndFlush(any(Sale.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fiscalDocumentRepository.save(any(FiscalDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldUseCurrentNumberAndIncrementEstablishmentForNextSale() {
        service.create(request());

        ArgumentCaptor<FiscalDocument> documentCaptor = ArgumentCaptor.forClass(FiscalDocument.class);
        verify(fiscalDocumentRepository).save(documentCaptor.capture());

        FiscalDocument document = documentCaptor.getValue();
        assertThat(document.getSeries()).isEqualTo(3);
        assertThat(document.getNumber()).isEqualTo(500L);
        assertThat(establishment.getNextNumber()).isEqualTo(501L);
    }

    @Test
    void shouldAllocateSequentialNumbersAcrossTwoCreations() {
        service.create(request());
        service.create(request());

        ArgumentCaptor<FiscalDocument> documentCaptor = ArgumentCaptor.forClass(FiscalDocument.class);
        verify(fiscalDocumentRepository, org.mockito.Mockito.times(2)).save(documentCaptor.capture());

        assertThat(documentCaptor.getAllValues())
            .extracting(FiscalDocument::getNumber)
            .containsExactly(500L, 501L);
        assertThat(establishment.getNextNumber()).isEqualTo(502L);
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