package net.ddns.lexdev.systempro_api.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;

import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.FiscalEmissionType;
import net.ddns.lexdev.systempro_api.enums.SaleStatus;

public interface SaleSummaryProjection {
    Long getId();
    SaleStatus getStatus();
    Instant getSaleAt();
    BigDecimal getTotal();
    Long getFiscalDocumentId();
    Long getFiscalNumber();
    FiscalEmissionType getFiscalEmissionType();
    FiscalDocumentStatus getFiscalStatus();
    String getFiscalAccessKey();
    String getFiscalProtocol();
}