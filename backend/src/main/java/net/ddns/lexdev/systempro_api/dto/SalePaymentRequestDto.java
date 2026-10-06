package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import net.ddns.lexdev.systempro_api.enums.PaymentMethod;

public record SalePaymentRequestDto(
    @NotNull PaymentMethod paymentMethod,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    String cardBrand,
    String authorizationCode
) {}
