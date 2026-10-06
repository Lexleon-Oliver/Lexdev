package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.enums.SaleStatus;

public record SaleResponseDto(
    Long id, Long fiscalEstablishmentId, Long clientId, Long userId, SaleStatus status, Instant saleAt,
    BigDecimal subtotal, BigDecimal discount, BigDecimal total, BigDecimal totalPaid, BigDecimal change,
    List<SaleItemResponseDto> items, List<SalePaymentResponseDto> payments, FiscalDocumentResponseDto fiscalDocument,
    String note
) {
    public static SaleResponseDto fromEntity(Sale s) {
        BigDecimal paid = s.getPayments().stream().map(p -> p.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal change = paid.subtract(s.getTotal()).max(BigDecimal.ZERO);
        return new SaleResponseDto(s.getId(), s.getFiscalEstablishment().getId(), s.getClient() == null ? null : s.getClient().getId(),
            s.getUser().getId(), s.getStatus(), s.getSaleAt(), s.getSubtotal(), s.getDiscount(), s.getTotal(), paid, change,
            s.getItems().stream().map(SaleItemResponseDto::fromEntity).toList(), s.getPayments().stream().map(SalePaymentResponseDto::fromEntity).toList(), null, s.getNote());
    }
}
