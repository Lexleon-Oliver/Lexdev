package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;
import java.time.Instant;

import net.ddns.lexdev.systempro_api.enums.SaleStatus;
import net.ddns.lexdev.systempro_api.repository.projection.SaleSummaryProjection;

public record SaleSummaryResponseDto(
    Long id,
    SaleStatus status,
    Instant saleAt,
    BigDecimal total,
    FiscalDocumentSummaryResponseDto fiscalDocument
) {
    public static SaleSummaryResponseDto fromProjection(SaleSummaryProjection projection) {
        return new SaleSummaryResponseDto(
            projection.getId(),
            projection.getStatus(),
            projection.getSaleAt(),
            projection.getTotal(),
            FiscalDocumentSummaryResponseDto.fromProjection(projection)
        );
    }
}