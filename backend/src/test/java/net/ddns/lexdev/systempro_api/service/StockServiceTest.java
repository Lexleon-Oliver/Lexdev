package net.ddns.lexdev.systempro_api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.lexdev.systempro_api.domain.Product;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.domain.StockBalance;
import net.ddns.lexdev.systempro_api.domain.StockMovement;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.enums.StockMovementOrigin;
import net.ddns.lexdev.systempro_api.enums.StockMovementType;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.repository.StockBalanceRepository;
import net.ddns.lexdev.systempro_api.repository.StockMovementRepository;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock ProductRepository productRepository;
    @Mock StockBalanceRepository balanceRepository;
    @Mock StockMovementRepository movementRepository;
    @Mock CurrentUserProvider currentUserProvider;

    private StockService service;
    private Product product;
    private User user;

    @BeforeEach
    void setUp() {
        service = new StockService(
            productRepository,
            balanceRepository,
            movementRepository,
            currentUserProvider
        );
        product = new Product();
        product.setControlsStock(true);
        ReflectionTestUtils.setField(product, "id", 10L);
        user = new User();
    }

    @Test
    void shouldInitializeBalanceAndCreateAuditableMovement() {
        when(productRepository.findByIdForStockUpdate(10L)).thenReturn(Optional.of(product));
        when(movementRepository.findByOriginAndSourceReference(
            StockMovementOrigin.INITIAL_BALANCE, "op-1"
        )).thenReturn(Optional.empty());
        when(balanceRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.empty());
        when(currentUserProvider.requireUser()).thenReturn(user);
        when(balanceRepository.save(any(StockBalance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(movementRepository.save(any(StockMovement.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        StockMovement result = service.initializeBalance(
            10L, new BigDecimal("12.500000"), "Inventário inicial", " op-1 "
        );

        assertEquals(StockMovementType.INITIAL_BALANCE, result.getMovementType());
        assertEquals(StockMovementOrigin.INITIAL_BALANCE, result.getOrigin());
        assertEquals(new BigDecimal("12.500000"), result.getQuantity());
        assertEquals(BigDecimal.ZERO, result.getPreviousBalance());
        assertEquals(new BigDecimal("12.500000"), result.getResultingBalance());
        assertEquals("Inventário inicial", result.getReason());
        assertSame(user, result.getCreatedBy());
    }

    @Test
    void shouldReturnExistingInitialMovementForSameIdempotencyReference() {
        StockMovement existing = movement(
            StockMovementType.INITIAL_BALANCE,
            StockMovementOrigin.INITIAL_BALANCE,
            "op-1",
            new BigDecimal("5")
        );
        when(productRepository.findByIdForStockUpdate(10L)).thenReturn(Optional.of(product));
        when(movementRepository.findByOriginAndSourceReference(
            StockMovementOrigin.INITIAL_BALANCE, "op-1"
        )).thenReturn(Optional.of(existing));

        StockMovement result = service.initializeBalance(10L, new BigDecimal("5.0"), "Inicial", "op-1");

        assertSame(existing, result);
        verify(balanceRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void shouldRejectSecondInitialBalance() {
        when(productRepository.findByIdForStockUpdate(10L)).thenReturn(Optional.of(product));
        when(movementRepository.findByOriginAndSourceReference(
            StockMovementOrigin.INITIAL_BALANCE, "op-2"
        )).thenReturn(Optional.empty());
        when(balanceRepository.findByProductIdForUpdate(10L))
            .thenReturn(Optional.of(new StockBalance(product, BigDecimal.TEN)));

        assertThrows(BusinessException.class, () ->
            service.initializeBalance(10L, BigDecimal.ONE, "Outro", "op-2")
        );
        verify(movementRepository, never()).save(any());
    }

    @Test
    void shouldCreatePositiveAdjustment() {
        StockBalance balance = new StockBalance(product, new BigDecimal("10"));
        stubAdjustment("adj-1", balance);

        StockMovement result = service.adjustPositive(10L, new BigDecimal("2.5"), "Contagem", "adj-1");

        assertEquals(new BigDecimal("12.5"), balance.getQuantity());
        assertEquals(new BigDecimal("10"), result.getPreviousBalance());
        assertEquals(new BigDecimal("12.5"), result.getResultingBalance());
        assertEquals(StockMovementType.POSITIVE_ADJUSTMENT, result.getMovementType());
    }

    @Test
    void shouldCreateNegativeAdjustment() {
        StockBalance balance = new StockBalance(product, new BigDecimal("10"));
        stubAdjustment("adj-2", balance);

        StockMovement result = service.adjustNegative(10L, new BigDecimal("3"), "Avaria", "adj-2");

        assertEquals(new BigDecimal("7"), balance.getQuantity());
        assertEquals(StockMovementType.NEGATIVE_ADJUSTMENT, result.getMovementType());
    }

    @Test
    void shouldRejectNegativeAdjustmentWhenStockIsInsufficient() {
        StockBalance balance = new StockBalance(product, new BigDecimal("2"));
        when(productRepository.findByIdForStockUpdate(10L)).thenReturn(Optional.of(product));
        when(movementRepository.findByOriginAndSourceReference(
            StockMovementOrigin.MANUAL_ADJUSTMENT, "adj-3"
        )).thenReturn(Optional.empty());
        when(balanceRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.of(balance));

        assertThrows(BusinessException.class, () ->
            service.adjustNegative(10L, new BigDecimal("3"), "Avaria", "adj-3")
        );

        assertEquals(new BigDecimal("2"), balance.getQuantity());
        verify(currentUserProvider, never()).requireUser();
        verify(movementRepository, never()).save(any());
    }

    @Test
    void shouldRejectProductWithoutStockControl() {
        product.setControlsStock(false);
        when(productRepository.findByIdForStockUpdate(10L)).thenReturn(Optional.of(product));

        assertThrows(BusinessException.class, () ->
            service.adjustPositive(10L, BigDecimal.ONE, "Ajuste", "adj-4")
        );
        verify(balanceRepository, never()).findByProductIdForUpdate(any());
    }

    @Test
    void shouldRejectAdjustmentBeforeInitialBalance() {
        when(productRepository.findByIdForStockUpdate(10L)).thenReturn(Optional.of(product));
        when(movementRepository.findByOriginAndSourceReference(
            StockMovementOrigin.MANUAL_ADJUSTMENT, "adj-5"
        )).thenReturn(Optional.empty());
        when(balanceRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () ->
            service.adjustPositive(10L, BigDecimal.ONE, "Ajuste", "adj-5")
        );
        verify(movementRepository, never()).save(any());
    }

    @Test
    void shouldRejectReusedReferenceWithDifferentAdjustmentData() {
        StockMovement existing = movement(
            StockMovementType.POSITIVE_ADJUSTMENT,
            StockMovementOrigin.MANUAL_ADJUSTMENT,
            "adj-6",
            BigDecimal.ONE
        );
        when(productRepository.findByIdForStockUpdate(10L)).thenReturn(Optional.of(product));
        when(movementRepository.findByOriginAndSourceReference(
            StockMovementOrigin.MANUAL_ADJUSTMENT, "adj-6"
        )).thenReturn(Optional.of(existing));

        assertThrows(BusinessException.class, () ->
            service.adjustPositive(10L, new BigDecimal("2"), "Ajuste", "adj-6")
        );
        verify(balanceRepository, never()).findByProductIdForUpdate(any());
    }

    @Test
    void shouldRegisterSaleOutAndReduceBalance() {
        Sale sale = saleWithItem(100L, 200L, product, new BigDecimal("3"));
        StockBalance balance = new StockBalance(product, new BigDecimal("10"));
        when(movementRepository.findByOriginAndSourceReference(StockMovementOrigin.SALE, "SALE_ITEM:200"))
            .thenReturn(Optional.empty());
        when(productRepository.findByIdForStockUpdate(10L)).thenReturn(Optional.of(product));
        when(balanceRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.of(balance));
        when(currentUserProvider.requireUser()).thenReturn(user);
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        service.registerSaleOut(sale);

        assertEquals(new BigDecimal("7"), balance.getQuantity());
        verify(movementRepository).save(org.mockito.ArgumentMatchers.argThat(m ->
            m.getMovementType() == StockMovementType.SALE_OUT
                && m.getOrigin() == StockMovementOrigin.SALE
                && m.getQuantity().compareTo(new BigDecimal("3")) == 0
                && "SALE_ITEM:200".equals(m.getSourceReference())
        ));
    }

    @Test
    void shouldRejectSaleOutWhenBalanceIsInsufficient() {
        Sale sale = saleWithItem(100L, 200L, product, new BigDecimal("3"));
        StockBalance balance = new StockBalance(product, new BigDecimal("2"));
        when(movementRepository.findByOriginAndSourceReference(StockMovementOrigin.SALE, "SALE_ITEM:200"))
            .thenReturn(Optional.empty());
        when(productRepository.findByIdForStockUpdate(10L)).thenReturn(Optional.of(product));
        when(balanceRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.of(balance));

        assertThrows(BusinessException.class, () -> service.registerSaleOut(sale));

        assertEquals(new BigDecimal("2"), balance.getQuantity());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void shouldReturnOnlyStockThatWasActuallyRemovedBySaleAndBeIdempotent() {
        Sale sale = saleWithItem(100L, 200L, product, new BigDecimal("3"));
        SaleItem item = sale.getItems().getFirst();
        StockMovement original = new StockMovement(
            product, StockMovementType.SALE_OUT, StockMovementOrigin.SALE, "SALE_ITEM:200",
            new BigDecimal("3"), new BigDecimal("10"), new BigDecimal("7"), sale, item, "Venda", user
        );
        StockBalance balance = new StockBalance(product, new BigDecimal("7"));
        when(movementRepository.findByOriginAndSourceReference(StockMovementOrigin.SALE, "SALE_ITEM:200"))
            .thenReturn(Optional.of(original));
        when(movementRepository.findByOriginAndSourceReference(
            StockMovementOrigin.SALE_CANCELLATION, "SALE_CANCELLATION_ITEM:200"
        )).thenReturn(Optional.empty());
        when(productRepository.findByIdForStockUpdate(10L)).thenReturn(Optional.of(product));
        when(balanceRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.of(balance));
        when(currentUserProvider.requireUser()).thenReturn(user);
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        service.registerSaleCancellationReturn(sale);

        assertEquals(new BigDecimal("10"), balance.getQuantity());
        verify(movementRepository).save(org.mockito.ArgumentMatchers.argThat(m ->
            m.getMovementType() == StockMovementType.SALE_CANCELLATION_RETURN
                && m.getOrigin() == StockMovementOrigin.SALE_CANCELLATION
        ));
    }

    private Sale saleWithItem(Long saleId, Long itemId, Product itemProduct, BigDecimal quantity) {
        Sale sale = new Sale();
        ReflectionTestUtils.setField(sale, "id", saleId);
        SaleItem item = new SaleItem();
        ReflectionTestUtils.setField(item, "id", itemId);
        item.setProduct(itemProduct);
        item.setQuantity(quantity);
        sale.addItem(item);
        return sale;
    }

    private void stubAdjustment(String reference, StockBalance balance) {
        when(productRepository.findByIdForStockUpdate(10L)).thenReturn(Optional.of(product));
        when(movementRepository.findByOriginAndSourceReference(
            StockMovementOrigin.MANUAL_ADJUSTMENT, reference
        )).thenReturn(Optional.empty());
        when(balanceRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.of(balance));
        when(currentUserProvider.requireUser()).thenReturn(user);
        when(movementRepository.save(any(StockMovement.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private StockMovement movement(
        StockMovementType type,
        StockMovementOrigin origin,
        String reference,
        BigDecimal quantity
    ) {
        return new StockMovement(
            product, type, origin, reference, quantity,
            BigDecimal.ZERO, quantity, null, null, "Motivo", user
        );
    }
}