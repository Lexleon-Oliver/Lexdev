package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;

import net.ddns.lexdev.systempro_api.domain.SaleItem;

public record SaleItemResponseDto(
    Long id, int itemNumber, Long productId, String code, String name, String unit,
    BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount, BigDecimal total
) {
    public static SaleItemResponseDto fromEntity(SaleItem i) {
        return new SaleItemResponseDto(i.getId(), i.getItemNumber(), i.getProduct().getId(), i.getCodeSnapshot(),
            i.getNameSnapshot(), i.getUnitSnapshot(), i.getQuantity(), i.getUnitPrice(), i.getDiscount(), i.getTotal());
    }
}
