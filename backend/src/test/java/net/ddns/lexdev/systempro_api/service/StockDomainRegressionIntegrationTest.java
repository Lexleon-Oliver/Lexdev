package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import net.ddns.lexdev.systempro_api.domain.Company;
import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.Product;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.dto.report.StockReportItemDto;
import net.ddns.lexdev.systempro_api.dto.report.StockReportResponseDto;
import net.ddns.lexdev.systempro_api.enums.FiscalEnvironment;
import net.ddns.lexdev.systempro_api.enums.ProductStatus;
import net.ddns.lexdev.systempro_api.enums.SaleStatus;
import net.ddns.lexdev.systempro_api.enums.StockMovementOrigin;
import net.ddns.lexdev.systempro_api.enums.StockMovementType;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.integration.IntegrationTestBase;
import net.ddns.lexdev.systempro_api.repository.SaleRepository;
import net.ddns.lexdev.systempro_api.repository.StockBalanceRepository;
import net.ddns.lexdev.systempro_api.repository.StockMovementRepository;

class StockDomainRegressionIntegrationTest extends IntegrationTestBase {

    @Autowired private StockService stockService;
    @Autowired private ReportService reportService;
    @Autowired private SaleRepository saleRepository;
    @Autowired private StockBalanceRepository balanceRepository;
    @Autowired private StockMovementRepository movementRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;

    @MockitoBean private CurrentUserProvider currentUserProvider;

    private User user;
    private FiscalEstablishment establishment;

    @BeforeEach
    void setUp() {
        Fixture fixture = inTransaction(this::createBaseFixture);
        user = fixture.user();
        establishment = fixture.establishment();
        when(currentUserProvider.requireUser()).thenReturn(user);
    }

    @Test
    void shouldKeepLedgerBalanceAndReportConsistentThroughSaleAndCancellation() {
        Product product = createProduct(true, "REG-A");

        stockService.initializeBalance(product.getId(), bd("10"), "Saldo inicial", ref("init"));
        stockService.adjustPositive(product.getId(), bd("2"), "Contagem", ref("adj"));
        Long saleId = createSale(product.getId(), bd("3"));

        inTransaction(() -> {
            Sale sale = saleRepository.findDetailed(saleId).orElseThrow();
            stockService.registerSaleOut(sale);
            return null;
        });
        assertBalance(product.getId(), "9");

        inTransaction(() -> {
            Sale sale = saleRepository.findDetailed(saleId).orElseThrow();
            sale.setStatus(SaleStatus.CANCELADA);
            stockService.registerSaleCancellationReturn(sale);
            return null;
        });
        assertBalance(product.getId(), "12");

        // Reprocessamento do retorno deve ser idempotente.
        inTransaction(() -> {
            Sale sale = saleRepository.findDetailed(saleId).orElseThrow();
            stockService.registerSaleCancellationReturn(sale);
            return null;
        });
        assertBalance(product.getId(), "12");

        assertThat(movementRepository.findAll().stream()
            .filter(m -> m.getProduct().getId().equals(product.getId()))
            .map(m -> m.getMovementType()).toList())
            .containsExactlyInAnyOrder(
                StockMovementType.INITIAL_BALANCE,
                StockMovementType.POSITIVE_ADJUSTMENT,
                StockMovementType.SALE_OUT,
                StockMovementType.SALE_CANCELLATION_RETURN
            );

        StockReportResponseDto report = reportService.stock(LocalDate.now(), LocalDate.now());
        StockReportItemDto item = report.items().stream()
            .filter(row -> row.productId().equals(product.getId()))
            .findFirst().orElseThrow();

        assertThat(item.currentBalance()).isEqualByComparingTo("12");
        assertThat(item.stockEntries()).isEqualByComparingTo("15");
        assertThat(item.stockOutputs()).isEqualByComparingTo("3");
        assertThat(item.quantitySold()).isEqualByComparingTo("0");
        assertThat(item.salesValue()).isEqualByComparingTo("0");
    }

    @Test
    void shouldRollbackEverySaleOutWhenLaterItemHasInsufficientStock() {
        Product first = createProduct(true, "REG-B1");
        Product second = createProduct(true, "REG-B2");
        stockService.initializeBalance(first.getId(), bd("10"), "Saldo inicial", ref("init-b1"));
        stockService.initializeBalance(second.getId(), bd("1"), "Saldo inicial", ref("init-b2"));
        Long saleId = createSale(first.getId(), bd("2"), second.getId(), bd("3"));

        assertThatThrownBy(() -> inTransaction(() -> {
            Sale sale = saleRepository.findDetailed(saleId).orElseThrow();
            stockService.registerSaleOut(sale);
            return null;
        })).isInstanceOf(BusinessException.class)
            .hasMessageContaining("Saldo de estoque insuficiente");

        assertBalance(first.getId(), "10");
        assertBalance(second.getId(), "1");
        assertThat(movementRepository.findAll().stream()
            .filter(m -> m.getOrigin() == StockMovementOrigin.SALE)
            .filter(m -> m.getSale() != null && m.getSale().getId().equals(saleId)))
            .isEmpty();
    }

    @Test
    void shouldIgnoreProductWithoutStockControlAndNeverCreatePhysicalMovement() {
        Product product = createProduct(false, "REG-C");
        Long saleId = createSale(product.getId(), bd("4"));

        inTransaction(() -> {
            Sale sale = saleRepository.findDetailed(saleId).orElseThrow();
            stockService.registerSaleOut(sale);
            return null;
        });

        assertThat(balanceRepository.findByProductId(product.getId())).isEmpty();
        assertThat(movementRepository.findAll().stream()
            .filter(m -> m.getProduct().getId().equals(product.getId())))
            .isEmpty();
    }

    private Product createProduct(boolean controlsStock, String prefix) {
        return inTransaction(() -> {
            Product product = new Product();
            product.setCode(prefix + "-" + System.nanoTime());
            product.setName("Produto regressão " + prefix);
            product.setUnitOfMeasure("UN");
            product.setControlsStock(controlsStock);
            product.setStatus(ProductStatus.ATIVO);
            product.setActive(true);
            product.setSalePrice(bd("10"));
            entityManager.persist(product);
            entityManager.flush();
            return product;
        });
    }

    private Long createSale(Long productId, BigDecimal quantity) {
        return createSale(productId, quantity, null, null);
    }

    private Long createSale(Long firstProductId, BigDecimal firstQuantity, Long secondProductId, BigDecimal secondQuantity) {
        return inTransaction(() -> {
            Sale sale = new Sale();
            sale.setFiscalEstablishment(entityManager.getReference(FiscalEstablishment.class, establishment.getId()));
            sale.setUser(entityManager.getReference(User.class, user.getId()));
            sale.setStatus(SaleStatus.AGUARDANDO_FISCAL);
            addItem(sale, firstProductId, firstQuantity, 1);
            if (secondProductId != null) addItem(sale, secondProductId, secondQuantity, 2);
            BigDecimal total = sale.getItems().stream().map(SaleItem::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            sale.setSubtotal(total);
            sale.setDiscount(BigDecimal.ZERO);
            sale.setTotal(total);
            entityManager.persist(sale);
            entityManager.flush();
            return sale.getId();
        });
    }

    private void addItem(Sale sale, Long productId, BigDecimal quantity, int number) {
        Product product = entityManager.find(Product.class, productId);
        SaleItem item = new SaleItem();
        item.setProduct(product);
        item.setItemNumber(number);
        item.setCodeSnapshot(product.getCode());
        item.setNameSnapshot(product.getName());
        item.setUnitSnapshot(product.getUnitOfMeasure());
        item.setNcmSnapshot("22021000");
        item.setOriginSnapshot("0");
        item.setQuantity(quantity);
        item.setUnitPrice(bd("10"));
        item.setDiscount(BigDecimal.ZERO);
        item.setTotal(quantity.multiply(bd("10")));
        item.setCfopSnapshot("5102");
        item.setIcmsCstCsosnSnapshot("102");
        item.setPisCstSnapshot("49");
        item.setCofinsCstSnapshot("49");
        sale.addItem(item);
    }

    private Fixture createBaseFixture() {
        User persistedUser = new User(
            "stock-reg-" + System.nanoTime(), "Stock Regression", "stock-reg-" + System.nanoTime() + "@test.local",
            "test-password", "ROLE_ADMIN"
        );
        entityManager.persist(persistedUser);

        Person person = new Person();
        person.setTipoPessoa(TipoPessoa.PJ);
        person.setName("Empresa regressão estoque");
        person.setCpfCnpj(nextCnpj());
        Company company = new Company(person);
        entityManager.persist(company);

        FiscalEstablishment persistedEstablishment = new FiscalEstablishment(company);
        persistedEstablishment.setMunicipalityIbgeCode("3105608");
        persistedEstablishment.setTaxRegime(TaxRegime.SIMPLES_NACIONAL);
        persistedEstablishment.setEnvironment(FiscalEnvironment.HOMOLOGACAO);
        entityManager.persist(persistedEstablishment);
        entityManager.flush();
        return new Fixture(persistedUser, persistedEstablishment);
    }

    private void assertBalance(Long productId, String expected) {
        assertThat(balanceRepository.findByProductId(productId).orElseThrow().getQuantity())
            .isEqualByComparingTo(expected);
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            try {
                return work.call();
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
    private static String ref(String prefix) { return prefix + "-" + System.nanoTime(); }
    private static String nextCnpj() {
        long value = Math.floorMod(System.nanoTime(), 100_000_000_000_000L);
        return String.format("%014d", value);
    }

    private record Fixture(User user, FiscalEstablishment establishment) {}
}