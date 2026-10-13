package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;

import net.ddns.lexdev.systempro_api.domain.Product;

public record ProductSaleSummaryResponseDto(
    Long id,
    String code,
    String name,
    String gtin,
    String unitOfMeasure,
    BigDecimal salePrice,
    Boolean controlsStock,
    Boolean controlsExpiration
) {
    public static ProductSaleSummaryResponseDto fromEntity(Product product) {
        return new ProductSaleSummaryResponseDto(
            product.getId(), product.getCode(), product.getName(), product.getGtin(), product.getUnitOfMeasure(),
            product.getSalePrice(), product.isControlsStock(), product.isControlsExpiration()
        );
    }
}