package net.ddns.lexdev.systempro_api.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.domain.Product;
import net.ddns.lexdev.systempro_api.domain.ProductSupplier;
import net.ddns.lexdev.systempro_api.domain.Supplier;
import net.ddns.lexdev.systempro_api.dto.ProductRequestDto;
import net.ddns.lexdev.systempro_api.dto.ProductResponseDto;
import net.ddns.lexdev.systempro_api.dto.ProductSummaryResponseDto;
import net.ddns.lexdev.systempro_api.dto.ProductSaleSummaryResponseDto;
import net.ddns.lexdev.systempro_api.dto.ProductSupplierRequestDto;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.repository.SupplierRepository;
import net.ddns.lexdev.systempro_api.repository.StockBalanceRepository;
import net.ddns.lexdev.systempro_api.repository.StockLotRepository;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import java.math.BigDecimal;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final StockLotRepository stockLotRepository;

    public ProductService(
        ProductRepository productRepository,
        SupplierRepository supplierRepository,
        StockBalanceRepository stockBalanceRepository,
        StockLotRepository stockLotRepository
    ) {
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.stockBalanceRepository = stockBalanceRepository;
        this.stockLotRepository = stockLotRepository;
    }

    // ============================================================
    // CREATE
    // ============================================================

    @Transactional
    public ProductResponseDto create(ProductRequestDto dto) {

        validateUniqueCode(dto.code(), null);
        validateUniqueGtin(dto.gtin(), null);
        validateExpirationControlRequest(dto);

        Product product = new Product();

        applyBasicData(product, dto);
        updateSuppliers(product, dto.suppliers());

        Product saved = productRepository.save(product);

        return ProductResponseDto.fromEntity(saved);
    }

    // ============================================================
    // UPDATE
    // ============================================================

    @Transactional
    public ProductResponseDto update(
        Long id,
        ProductRequestDto dto
    ) {

        Product product = findEntity(id);

        validateUniqueCode(dto.code(), id);
        validateUniqueGtin(dto.gtin(), id);
        validateExpirationControlRequest(dto);
        validateExpirationControlChange(product, dto);

        applyBasicData(product, dto);
        updateSuppliers(product, dto.suppliers());

        return ProductResponseDto.fromEntity(product);
    }

    // ============================================================
    // FIND BY ID
    // ============================================================

    @Transactional(readOnly = true)
    public ProductResponseDto findById(Long id) {

        Product product = productRepository
            .findByIdWithSuppliers(id)
            .orElseThrow(() ->
                new EntityNotFoundException(
                    "Produto não encontrado: " + id
                )
            );

        return ProductResponseDto.fromEntity(product);
    }

    // ============================================================
    // PAGINATED LIST
    // ============================================================

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponseDto> findAll(Pageable pageable) {
        return findAll(pageable, "");
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponseDto> findAll(
        Pageable pageable,
        String search
    ) {
        return findAll(pageable, search, null);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponseDto> findAll(
        Pageable pageable,
        String search,
        Boolean controlsStock
    ) {
        String normalizedSearch = search == null
            ? ""
            : search.trim();

        if (controlsStock != null) {
            return productRepository
                .findByStockControl(controlsStock, normalizedSearch, pageable)
                .map(ProductSummaryResponseDto::fromEntity);
        }

        if (normalizedSearch.isEmpty()) {
            return productRepository.findAllProducts(pageable).map(ProductSummaryResponseDto::fromEntity);
        }

        return productRepository.search(normalizedSearch, pageable).map(ProductSummaryResponseDto::fromEntity);
    }

    // ============================================================
    // SEARCH
    // ============================================================

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponseDto> search(
        String search,
        Pageable pageable
    ) {

        if (search == null || search.isBlank()) {
            return findAll(pageable, "");
        }

        return productRepository
            .search(search.trim(), pageable)
            .map(ProductSummaryResponseDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<ProductSaleSummaryResponseDto> searchForSale(String search, Pageable pageable) {
        String normalizedSearch = search == null ? "" : search.trim();
        return productRepository.searchForSale(normalizedSearch, pageable)
            .map(ProductSaleSummaryResponseDto::fromEntity);
    }

    // ============================================================
    // SOFT DELETE
    // ============================================================

    @Transactional
    public void delete(Long id) {

        Product product = findEntity(id);

        productRepository.delete(product);
    }

    private void validateExpirationControlRequest(ProductRequestDto dto) {
        if (Boolean.TRUE.equals(dto.controlsExpiration()) && !Boolean.TRUE.equals(dto.controlsStock())) {
            throw new BusinessException("Controle de validade exige controle de estoque habilitado.");
        }
    }

    private void validateExpirationControlChange(Product product, ProductRequestDto dto) {
        boolean requested = Boolean.TRUE.equals(dto.controlsExpiration());
        if (product.isControlsExpiration() == requested) return;
        boolean hasBalance = stockBalanceRepository.findByProductId(product.getId())
            .map(balance -> balance.getQuantity().compareTo(BigDecimal.ZERO) > 0).orElse(false);
        boolean hasLots = stockLotRepository.existsByProductIdAndQuantityGreaterThan(product.getId(), BigDecimal.ZERO);
        if (hasBalance || hasLots) {
            throw new BusinessException("Não é possível alterar o controle de validade enquanto o produto possui saldo em estoque.");
        }
    }

    // ============================================================
    // BASIC DATA
    // ============================================================

    private void applyBasicData(
        Product product,
        ProductRequestDto dto
    ) {

        product.setCode(dto.code());
        product.setName(dto.name());
        product.setDescription(dto.description());
        product.setModel(dto.model());
        product.setManufacturerCode(dto.manufacturerCode());
        product.setGtin(dto.gtin());
        product.setStatus(dto.status());
        product.setUnitOfMeasure(dto.unitOfMeasure());
        product.setControlsStock(dto.controlsStock());
        product.setControlsExpiration(Boolean.TRUE.equals(dto.controlsExpiration()));

        product.setSalePrice(dto.salePrice());
        product.setMinimumSalePrice(dto.minimumSalePrice());

        product.setMinimumStock(dto.minimumStock());
        product.setMaximumStock(dto.maximumStock());
        product.setReorderPoint(dto.reorderPoint());

        product.setNcm(dto.ncm());
        product.setCest(dto.cest());
        product.setOrigin(dto.origin());

        product.setGrossWeight(dto.grossWeight());
        product.setNetWeight(dto.netWeight());

        product.setHeight(dto.height());
        product.setWidth(dto.width());
        product.setLength(dto.length());
    }

    // ============================================================
    // SUPPLIERS
    // ============================================================

    private void updateSuppliers(
        Product product,
        List<ProductSupplierRequestDto> supplierDtos
    ) {

        product.getSuppliers().clear();

        /*
        * Garante que os ProductSupplier removidos por
        * orphanRemoval sejam excluídos antes de inserir
        * novamente os mesmos vínculos.
        */
        if (product.getId() != null) {
            productRepository.flush();
        }

        if (supplierDtos == null || supplierDtos.isEmpty()) {
            return;
        }

        validatePreferredSupplier(supplierDtos);
        validateDuplicateSuppliers(supplierDtos);

        for (ProductSupplierRequestDto dto : supplierDtos) {

            Supplier supplier = supplierRepository
                .findById(dto.supplierId())
                .orElseThrow(() ->
                    new EntityNotFoundException(
                        "Fornecedor não encontrado: " +
                        dto.supplierId()
                    )
                );

            ProductSupplier productSupplier =
                new ProductSupplier();

            productSupplier.setSupplier(supplier);
            productSupplier.setSupplierCode(
                dto.supplierCode()
            );
            productSupplier.setPurchasePrice(
                dto.purchasePrice()
            );
            productSupplier.setLeadTimeDays(
                dto.leadTimeDays()
            );
            productSupplier.setMinimumOrderQuantity(
                dto.minimumOrderQuantity()
            );
            productSupplier.setPreferred(
                Boolean.TRUE.equals(dto.preferred())
            );

            product.addSupplier(productSupplier);
        }
    }

    private void validatePreferredSupplier(
        List<ProductSupplierRequestDto> suppliers
    ) {

        long preferredCount = suppliers
            .stream()
            .filter(dto -> Boolean.TRUE.equals(dto.preferred()))
            .count();

        if (preferredCount > 1) {
            throw new IllegalArgumentException(
                "O produto pode possuir apenas um fornecedor principal."
            );
        }
    }

    // ============================================================
    // VALIDATIONS
    // ============================================================

    private void validateUniqueCode(
        String code,
        Long currentId
    ) {

        if (!productRepository.existsByCode(code)) {
            return;
        }

        if (currentId == null) {
            throw new IllegalArgumentException(
                "Já existe um produto cadastrado com o código: " + code
            );
        }

        Product existing = productRepository
            .findById(currentId)
            .orElseThrow(() ->
                new EntityNotFoundException(
                    "Produto não encontrado: " + currentId
                )
            );

        if (!existing.getCode().equals(code)) {
            throw new IllegalArgumentException(
                "Já existe um produto cadastrado com o código: " + code
            );
        }
    }

    private void validateUniqueGtin(
        String gtin,
        Long currentId
    ) {

        if (gtin == null || gtin.isBlank()) {
            return;
        }

        productRepository
            .findByGtin(gtin)
            .ifPresent(existing -> {

                if (currentId == null ||
                    !existing.getId().equals(currentId)) {

                    throw new IllegalArgumentException(
                        "Já existe um produto cadastrado com o GTIN: " + gtin
                    );
                }
            });
    }

    private void validateDuplicateSuppliers(
        List<ProductSupplierRequestDto> suppliers
    ) {

        long distinctCount = suppliers
            .stream()
            .map(ProductSupplierRequestDto::supplierId)
            .distinct()
            .count();

        if (distinctCount != suppliers.size()) {
            throw new IllegalArgumentException(
                "O mesmo fornecedor não pode ser vinculado " +
                "mais de uma vez ao mesmo produto."
            );
        }
    }

    // ============================================================
    // ENTITY
    // ============================================================

    private Product findEntity(Long id) {

        return productRepository
            .findById(id)
            .orElseThrow(() ->
                new EntityNotFoundException(
                    "Produto não encontrado: " + id
                )
            );
    }
}