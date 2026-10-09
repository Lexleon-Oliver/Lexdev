package net.ddns.lexdev.systempro_api.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface FinancialDailyProjection {
    LocalDate getDate();
    Long getSales();
    BigDecimal getTotal();
}