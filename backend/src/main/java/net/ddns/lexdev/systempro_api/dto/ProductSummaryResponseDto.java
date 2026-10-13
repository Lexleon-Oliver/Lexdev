package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;

import net.ddns.lexdev.systempro_api.domain.Product;
import net.ddns.lexdev.systempro_api.enums.ProductStatus;

public record ProductSummaryResponseDto(
    Long id,
    String code,
    String name,
    String model,
    String gtin,
    ProductStatus status,
    String unitOfMeasure,
    Boolean controlsStock,
    Boolean controlsExpiration,
    BigDecimal salePrice,
    BigDecimal minimumStock,
    Boolean active
) {
    public static ProductSummaryResponseDto fromEntity(Product product) {
        return new ProductSummaryResponseDto(
            product.getId(), product.getCode(), product.getName(), product.getModel(), product.getGtin(),
            product.getStatus(), product.getUnitOfMeasure(), product.isControlsStock(), product.isControlsExpiration(),
            product.getSalePrice(), product.getMinimumStock(), product.isActive()
        );
    }
}