package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;
import java.util.List;

import net.ddns.lexdev.systempro_api.domain.Product;
import net.ddns.lexdev.systempro_api.enums.ProductStatus;

public record ProductResponseDto(

    Long id,

    String code,

    String name,

    String description,

    String model,

    String manufacturerCode,

    String gtin,

    ProductStatus status,

    String unitOfMeasure,

    Boolean controlsStock,

    BigDecimal salePrice,

    BigDecimal minimumSalePrice,

    BigDecimal minimumStock,

    BigDecimal maximumStock,

    BigDecimal reorderPoint,

    String ncm,

    String cest,

    String origin,

    BigDecimal grossWeight,

    BigDecimal netWeight,

    BigDecimal height,

    BigDecimal width,

    BigDecimal length,

    List<ProductSupplierResponseDto> suppliers,

    List<ProductImageResponseDto> images,

    Boolean active

) {

    public static ProductResponseDto fromEntity(Product product) {

        List<ProductSupplierResponseDto> suppliers =
            product.getSuppliers()
                .stream()
                .map(supplier -> new ProductSupplierResponseDto(
                    supplier.getId(),
                    supplier.getSupplier().getId(),
                    supplier.getSupplierCode(),
                    supplier.getPurchasePrice(),
                    supplier.getLeadTimeDays(),
                    supplier.getMinimumOrderQuantity(),
                    supplier.isPreferred()
                ))
                .toList();

        List<ProductImageResponseDto> images =
            product.getImages()
                .stream()
                .map(image -> new ProductImageResponseDto(
                    image.getId(),
                    image.getFileName(),
                    image.getContentType(),
                    image.getFileSize(),
                    image.isMainImage(),
                    image.getSortOrder(),
                    "/api/products/"
                        + product.getId()
                        + "/images/"
                        + image.getId()
                        + "/content"
                ))
                .toList();
        return new ProductResponseDto(
            product.getId(),
            product.getCode(),
            product.getName(),
            product.getDescription(),
            product.getModel(),
            product.getManufacturerCode(),
            product.getGtin(),
            product.getStatus(),
            product.getUnitOfMeasure(),
            product.isControlsStock(),
            product.getSalePrice(),
            product.getMinimumSalePrice(),
            product.getMinimumStock(),
            product.getMaximumStock(),
            product.getReorderPoint(),
            product.getNcm(),
            product.getCest(),
            product.getOrigin(),
            product.getGrossWeight(),
            product.getNetWeight(),
            product.getHeight(),
            product.getWidth(),
            product.getLength(),
            suppliers,
            images,
            product.isActive()
        );
    }
}