package net.ddns.lexdev.systempro_api.repository.projection;

import java.math.BigDecimal;

public interface StockReportProjection {
    Long getProductId();
    String getCode();
    String getName();
    String getUnitOfMeasure();
    Boolean getControlsStock();
    BigDecimal getMinimumStock();
    BigDecimal getMaximumStock();
    BigDecimal getReorderPoint();
    BigDecimal getSalePrice();
    BigDecimal getCurrentBalance();
    BigDecimal getQuantitySold();
    BigDecimal getSalesValue();
    BigDecimal getStockEntries();
    BigDecimal getStockOutputs();
}