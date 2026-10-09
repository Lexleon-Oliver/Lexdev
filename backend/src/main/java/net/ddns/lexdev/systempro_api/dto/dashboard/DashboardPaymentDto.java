package net.ddns.lexdev.systempro_api.dto.dashboard;

import java.math.BigDecimal;
import net.ddns.lexdev.systempro_api.enums.PaymentMethod;

public record DashboardPaymentDto(PaymentMethod paymentMethod, BigDecimal amount) {}