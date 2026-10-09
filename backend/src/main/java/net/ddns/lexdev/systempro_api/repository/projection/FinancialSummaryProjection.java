package net.ddns.lexdev.systempro_api.repository.projection;

import java.math.BigDecimal;

public interface FinancialSummaryProjection {
    Long getSales();
    BigDecimal getGrossSales();
    BigDecimal getDiscounts();
    BigDecimal getNetSales();
}