package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;
import net.ddns.lexdev.systempro_api.domain.SalePayment;
import net.ddns.lexdev.systempro_api.enums.PaymentMethod;

public record SalePaymentResponseDto(Long id, PaymentMethod paymentMethod, BigDecimal amount, String cardBrand, String authorizationCode) {
    public static SalePaymentResponseDto fromEntity(SalePayment p) {
        return new SalePaymentResponseDto(p.getId(), p.getPaymentMethod(), p.getAmount(), p.getCardBrand(), p.getAuthorizationCode());
    }
}
