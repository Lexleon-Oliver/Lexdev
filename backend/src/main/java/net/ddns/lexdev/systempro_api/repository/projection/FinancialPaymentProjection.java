package net.ddns.lexdev.systempro_api.repository.projection;

import java.math.BigDecimal;

public interface FinancialPaymentProjection {
    String getPaymentMethod();
    BigDecimal getAmount();
}