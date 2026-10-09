package net.ddns.lexdev.systempro_api.service;

import java.math.BigDecimal;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.ddns.lexdev.systempro_api.domain.Product;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.domain.StockBalance;
import net.ddns.lexdev.systempro_api.domain.StockMovement;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.dto.stock.StockBalanceResponseDto;
import net.ddns.lexdev.systempro_api.dto.stock.StockMovementResponseDto;
import net.ddns.lexdev.systempro_api.enums.StockMovementOrigin;
import net.ddns.lexdev.systempro_api.enums.StockMovementType;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.repository.StockBalanceRepository;
import net.ddns.lexdev.systempro_api.repository.StockMovementRepository;

@Service
public class StockService {

    private final ProductRepository productRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockMovementRepository movementRepository;
    private final CurrentUserProvider currentUserProvider;

    public StockService(
        ProductRepository productRepository,
        StockBalanceRepository balanceRepository,
        StockMovementRepository movementRepository,
        CurrentUserProvider currentUserProvider
    ) {
        this.productRepository = productRepository;
        this.balanceRepository = balanceRepository;
        this.movementRepository = movementRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public StockMovement initializeBalance(
        Long productId,
        BigDecimal quantity,
        String reason,
        String operationReference
    ) {
        Product product = lockControlledProduct(productId);
        requirePositive(quantity);
        String reference = requireReference(operationReference);
        String normalizedReason = requireReason(reason);

        StockMovement existing = findExisting(StockMovementOrigin.INITIAL_BALANCE, reference);
        if (existing != null) {
            StockMovement sameProduct = requireSameProduct(existing, productId);
            if (sameProduct.getMovementType() != StockMovementType.INITIAL_BALANCE
                || sameProduct.getQuantity().compareTo(quantity) != 0) {
                throw new BusinessException(
                    "A referência da operação já foi utilizada com dados diferentes."
                );
            }
            return sameProduct;
        }

        if (balanceRepository.findByProductIdForUpdate(productId).isPresent()) {
            throw new BusinessException("O saldo inicial deste produto já foi informado.");
        }

        User user = currentUserProvider.requireUser();
        StockBalance balance = balanceRepository.save(new StockBalance(product, quantity));
        StockMovement movement = new StockMovement(
            product,
            StockMovementType.INITIAL_BALANCE,
            StockMovementOrigin.INITIAL_BALANCE,
            reference,
            quantity,
            BigDecimal.ZERO,
            balance.getQuantity(),
            null,
            null,
            normalizedReason,
            user
        );
        return saveMovement(movement);
    }

    @Transactional
    public StockMovement adjustPositive(
        Long productId,
        BigDecimal quantity,
        String reason,
        String operationReference
    ) {
        return adjust(productId, quantity, reason, operationReference, true);
    }

    @Transactional
    public StockMovement adjustNegative(
        Long productId,
        BigDecimal quantity,
        String reason,
        String operationReference
    ) {
        return adjust(productId, quantity, reason, operationReference, false);
    }


    @Transactional
    public void registerSaleOut(Sale sale) {
        if (sale == null || sale.getId() == null) {
            throw new BusinessException("A venda precisa estar persistida antes da baixa de estoque.");
        }

        sale.getItems().stream()
            .filter(item -> item.getProduct().isControlsStock())
            .sorted(java.util.Comparator
                .comparing((SaleItem item) -> item.getProduct().getId())
                .thenComparing(SaleItem::getId))
            .forEach(item -> registerSaleItemOut(sale, item));
    }

    @Transactional
    public void registerSaleCancellationReturn(Sale sale) {
        if (sale == null || sale.getId() == null) {
            throw new BusinessException("A venda precisa estar persistida para estornar o estoque.");
        }

        sale.getItems().stream()
            .sorted(java.util.Comparator
                .comparing((SaleItem item) -> item.getProduct().getId())
                .thenComparing(SaleItem::getId))
            .forEach(item -> registerSaleItemReturn(sale, item));
    }

    private void registerSaleItemOut(Sale sale, SaleItem item) {
        if (item.getId() == null) {
            throw new BusinessException("O item da venda precisa estar persistido antes da baixa de estoque.");
        }

        String reference = saleItemReference(item.getId());
        StockMovement existing = findExisting(StockMovementOrigin.SALE, reference);
        if (existing != null) {
            validateSaleMovement(existing, sale, item, StockMovementType.SALE_OUT, item.getQuantity());
            return;
        }

        Product product = lockControlledProduct(item.getProduct().getId());
        StockBalance balance = balanceRepository.findByProductIdForUpdate(product.getId())
            .orElseThrow(() -> new BusinessException(
                "O produto " + product.getCode() + " ainda não possui saldo inicial de estoque."
            ));

        BigDecimal previous = balance.getQuantity();
        BigDecimal resulting = previous.subtract(item.getQuantity());
        if (resulting.signum() < 0) {
            throw new BusinessException(
                "Saldo de estoque insuficiente para o produto " + product.getCode() + "."
            );
        }

        balance.setQuantity(resulting);
        saveMovement(new StockMovement(
            product,
            StockMovementType.SALE_OUT,
            StockMovementOrigin.SALE,
            reference,
            item.getQuantity(),
            previous,
            resulting,
            sale,
            item,
            "Saída pela venda " + sale.getId(),
            currentUserProvider.requireUser()
        ));
    }

    private void registerSaleItemReturn(Sale sale, SaleItem item) {
        if (item.getId() == null) {
            throw new BusinessException("O item da venda precisa estar persistido para estornar o estoque.");
        }

        String originalReference = saleItemReference(item.getId());
        StockMovement original = findExisting(StockMovementOrigin.SALE, originalReference);
        if (original == null) {
            return; // item não participou do estoque quando a venda foi concluída
        }
        validateSaleMovement(original, sale, item, StockMovementType.SALE_OUT, item.getQuantity());

        String returnReference = saleCancellationItemReference(item.getId());
        StockMovement existingReturn = findExisting(StockMovementOrigin.SALE_CANCELLATION, returnReference);
        if (existingReturn != null) {
            validateSaleMovement(
                existingReturn, sale, item, StockMovementType.SALE_CANCELLATION_RETURN, original.getQuantity()
            );
            return;
        }

        Product product = productRepository.findByIdForStockUpdate(item.getProduct().getId())
            .orElseThrow(() -> new BusinessException("Produto não encontrado."));
        StockBalance balance = balanceRepository.findByProductIdForUpdate(product.getId())
            .orElseThrow(() -> new BusinessException(
                "O saldo de estoque do produto " + product.getCode() + " não foi encontrado."
            ));

        BigDecimal previous = balance.getQuantity();
        BigDecimal resulting = previous.add(original.getQuantity());
        balance.setQuantity(resulting);
        saveMovement(new StockMovement(
            product,
            StockMovementType.SALE_CANCELLATION_RETURN,
            StockMovementOrigin.SALE_CANCELLATION,
            returnReference,
            original.getQuantity(),
            previous,
            resulting,
            sale,
            item,
            "Retorno pelo cancelamento da venda " + sale.getId(),
            currentUserProvider.requireUser()
        ));
    }

    private void validateSaleMovement(
        StockMovement movement,
        Sale sale,
        SaleItem item,
        StockMovementType expectedType,
        BigDecimal expectedQuantity
    ) {
        if (movement.getMovementType() != expectedType
            || movement.getSale() == null
            || !sale.getId().equals(movement.getSale().getId())
            || movement.getSaleItem() == null
            || !item.getId().equals(movement.getSaleItem().getId())
            || movement.getProduct() == null
            || !item.getProduct().getId().equals(movement.getProduct().getId())
            || movement.getQuantity().compareTo(expectedQuantity) != 0) {
            throw new BusinessException("A referência da movimentação de estoque da venda possui dados incompatíveis.");
        }
    }

    private String saleItemReference(Long saleItemId) {
        return "SALE_ITEM:" + saleItemId;
    }

    private String saleCancellationItemReference(Long saleItemId) {
        return "SALE_CANCELLATION_ITEM:" + saleItemId;
    }

    @Transactional(readOnly = true)
    public BigDecimal currentBalance(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException("Produto não encontrado."));
        requireStockControl(product);
        return balanceRepository.findByProductId(productId)
            .map(StockBalance::getQuantity)
            .orElseThrow(() -> new BusinessException(
                "O produto ainda não possui saldo inicial de estoque."
            ));
    }

    @Transactional(readOnly = true)
    public StockBalanceResponseDto stockBalance(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException("Produto não encontrado."));
        requireStockControl(product);

        return balanceRepository.findByProductId(productId)
            .map(balance -> new StockBalanceResponseDto(
                product.getId(), product.getCode(), product.getName(), product.getUnitOfMeasure(),
                true, true, balance.getQuantity()
            ))
            .orElseGet(() -> new StockBalanceResponseDto(
                product.getId(), product.getCode(), product.getName(), product.getUnitOfMeasure(),
                true, false, null
            ));
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponseDto> movements(Long productId, Pageable pageable) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException("Produto não encontrado."));
        requireStockControl(product);
        return movementRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId, pageable)
            .map(this::toResponse);
    }

    @Transactional
    public StockMovementResponseDto initializeBalanceResponse(
        Long productId, BigDecimal quantity, String reason, String operationReference
    ) {
        return toResponse(initializeBalance(productId, quantity, reason, operationReference));
    }

    @Transactional
    public StockMovementResponseDto adjustPositiveResponse(
        Long productId, BigDecimal quantity, String reason, String operationReference
    ) {
        return toResponse(adjustPositive(productId, quantity, reason, operationReference));
    }

    @Transactional
    public StockMovementResponseDto adjustNegativeResponse(
        Long productId, BigDecimal quantity, String reason, String operationReference
    ) {
        return toResponse(adjustNegative(productId, quantity, reason, operationReference));
    }

    private StockMovementResponseDto toResponse(StockMovement movement) {
        return new StockMovementResponseDto(
            movement.getId(),
            movement.getProduct().getId(),
            movement.getMovementType(),
            movement.getOrigin(),
            movement.getSourceReference(),
            movement.getQuantity(),
            movement.getPreviousBalance(),
            movement.getResultingBalance(),
            movement.getSale() == null ? null : movement.getSale().getId(),
            movement.getSaleItem() == null ? null : movement.getSaleItem().getId(),
            movement.getReason(),
            movement.getCreatedBy().getUsername(),
            movement.getCreatedAt()
        );
    }

    private StockMovement adjust(
        Long productId,
        BigDecimal quantity,
        String reason,
        String operationReference,
        boolean positive
    ) {
        Product product = lockControlledProduct(productId);
        requirePositive(quantity);
        String reference = requireReference(operationReference);
        String normalizedReason = requireReason(reason);

        StockMovement existing = findExisting(StockMovementOrigin.MANUAL_ADJUSTMENT, reference);
        if (existing != null) {
            StockMovement sameProduct = requireSameProduct(existing, productId);
            StockMovementType expected = positive
                ? StockMovementType.POSITIVE_ADJUSTMENT
                : StockMovementType.NEGATIVE_ADJUSTMENT;
            if (sameProduct.getMovementType() != expected
                || sameProduct.getQuantity().compareTo(quantity) != 0) {
                throw new BusinessException(
                    "A referência da operação já foi utilizada com dados diferentes."
                );
            }
            return sameProduct;
        }

        StockBalance balance = balanceRepository.findByProductIdForUpdate(productId)
            .orElseThrow(() -> new BusinessException(
                "O produto ainda não possui saldo inicial de estoque."
            ));

        BigDecimal previous = balance.getQuantity();
        BigDecimal resulting = positive
            ? previous.add(quantity)
            : previous.subtract(quantity);

        if (resulting.signum() < 0) {
            throw new BusinessException(
                "Saldo de estoque insuficiente para realizar a operação."
            );
        }

        balance.setQuantity(resulting);
        User user = currentUserProvider.requireUser();
        StockMovement movement = new StockMovement(
            product,
            positive ? StockMovementType.POSITIVE_ADJUSTMENT : StockMovementType.NEGATIVE_ADJUSTMENT,
            StockMovementOrigin.MANUAL_ADJUSTMENT,
            reference,
            quantity,
            previous,
            resulting,
            null,
            null,
            normalizedReason,
            user
        );
        return saveMovement(movement);
    }

    private Product lockControlledProduct(Long productId) {
        Product product = productRepository.findByIdForStockUpdate(productId)
            .orElseThrow(() -> new BusinessException("Produto não encontrado."));
        requireStockControl(product);
        return product;
    }

    private void requireStockControl(Product product) {
        if (!product.isControlsStock()) {
            throw new BusinessException("O produto não possui controle de estoque habilitado.");
        }
    }

    private void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("A quantidade deve ser maior que zero.");
        }
    }

    private String requireReference(String operationReference) {
        if (operationReference == null || operationReference.isBlank()) {
            throw new BusinessException("A referência da operação de estoque é obrigatória.");
        }
        String reference = operationReference.trim();
        if (reference.length() > 120) {
            throw new BusinessException("A referência da operação de estoque excede 120 caracteres.");
        }
        return reference;
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("O motivo da movimentação de estoque é obrigatório.");
        }
        String normalized = reason.trim();
        if (normalized.length() > 500) {
            throw new BusinessException("O motivo da movimentação de estoque excede 500 caracteres.");
        }
        return normalized;
    }

    private StockMovement findExisting(StockMovementOrigin origin, String reference) {
        return movementRepository.findByOriginAndSourceReference(origin, reference).orElse(null);
    }

    private StockMovement requireSameProduct(StockMovement movement, Long productId) {
        if (!movement.getProduct().getId().equals(productId)) {
            throw new BusinessException(
                "A referência da operação já foi utilizada para outro produto."
            );
        }
        return movement;
    }

    private StockMovement saveMovement(StockMovement movement) {
        try {
            return movementRepository.save(movement);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(
                "A referência da operação de estoque já foi utilizada.",
                ex
            );
        }
    }
}