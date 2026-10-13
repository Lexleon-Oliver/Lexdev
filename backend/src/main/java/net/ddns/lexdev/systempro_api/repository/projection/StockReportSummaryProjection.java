package net.ddns.lexdev.systempro_api.repository.projection;

import java.math.BigDecimal;

public interface StockReportSummaryProjection {
    Long getProducts();
    Long getStockControlledProducts();
    Long getInitializedStockProducts();
    Long getReplenishmentNeededProducts();
    BigDecimal getCurrentBalance();
    BigDecimal getStockEntries();
    BigDecimal getStockOutputs();
    BigDecimal getQuantitySold();
    BigDecimal getSalesValue();
}