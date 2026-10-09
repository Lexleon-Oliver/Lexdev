package net.ddns.lexdev.systempro_api.dto.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.SaleStatus;

public record DashboardRecentSaleDto(
    Long id,
    Instant saleAt,
    SaleStatus status,
    BigDecimal total,
    FiscalDocumentStatus fiscalStatus
) {}