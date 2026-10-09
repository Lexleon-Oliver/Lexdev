package net.ddns.lexdev.systempro_api.dto.report;

import java.math.BigDecimal;
import net.ddns.lexdev.systempro_api.enums.PaymentMethod;

public record FinancialPaymentDto(PaymentMethod paymentMethod, BigDecimal amount) {}