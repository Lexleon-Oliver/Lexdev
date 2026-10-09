package net.ddns.lexdev.systempro_api.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.SaleStatus;

public interface DashboardRecentSaleProjection {
    Long getId();
    Instant getSaleAt();
    SaleStatus getStatus();
    BigDecimal getTotal();
    FiscalDocumentStatus getFiscalStatus();
}