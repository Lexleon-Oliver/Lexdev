package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import net.ddns.lexdev.systempro_api.domain.Product;
import net.ddns.lexdev.systempro_api.domain.StockBalance;
import net.ddns.lexdev.systempro_api.domain.StockMovement;
import net.ddns.lexdev.systempro_api.dto.stock.StockBalanceResponseDto;
import net.ddns.lexdev.systempro_api.enums.StockMovementOrigin;
import net.ddns.lexdev.systempro_api.enums.StockMovementType;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.repository.StockBalanceRepository;
import net.ddns.lexdev.systempro_api.repository.StockMovementRepository;

class StockServiceOperationalTest {

    private ProductRepository productRepository;
    private StockBalanceRepository balanceRepository;
    private StockMovementRepository movementRepository;
    private StockService service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        balanceRepository = mock(StockBalanceRepository.class);
        movementRepository = mock(StockMovementRepository.class);
        service = new StockService(
            productRepository, balanceRepository, movementRepository, mock(CurrentUserProvider.class)
        );
    }

    @Test
    void returnsUninitializedPositionWithoutInventingZeroBalance() {
        Product product = controlledProduct();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(balanceRepository.findByProductId(10L)).thenReturn(Optional.empty());

        StockBalanceResponseDto response = service.stockBalance(10L);

        assertThat(response.initialized()).isFalse();
        assertThat(response.quantity()).isNull();
        assertThat(response.productId()).isEqualTo(10L);
    }

    @Test
    void returnsMaterializedBalanceWhenInitialized() {
        Product product = controlledProduct();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(balanceRepository.findByProductId(10L))
            .thenReturn(Optional.of(new StockBalance(product, new BigDecimal("12.500000"))));

        StockBalanceResponseDto response = service.stockBalance(10L);

        assertThat(response.initialized()).isTrue();
        assertThat(response.quantity()).isEqualByComparingTo("12.500000");
    }

    @Test
    void listsMovementsNewestFirstThroughRepositoryContract() {
        Product product = controlledProduct();
        var pageable = PageRequest.of(0, 20);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(movementRepository.findByProductIdOrderByCreatedAtDescIdDesc(10L, pageable))
            .thenReturn(new PageImpl<StockMovement>(List.of(), pageable, 0));

        var result = service.movements(10L, pageable);

        assertThat(result).isEmpty();
    }

    @Test
    void refusesOperationalStockForProductWithoutControl() {
        Product product = mock(Product.class);
        when(product.isControlsStock()).thenReturn(false);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.stockBalance(10L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("O produto não possui controle de estoque habilitado.");
    }

    private Product controlledProduct() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(10L);
        when(product.getCode()).thenReturn("P001");
        when(product.getName()).thenReturn("Produto teste");
        when(product.getUnitOfMeasure()).thenReturn("UN");
        when(product.isControlsStock()).thenReturn(true);
        return product;
    }
}