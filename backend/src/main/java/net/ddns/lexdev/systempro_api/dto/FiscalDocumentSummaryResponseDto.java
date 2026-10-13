package net.ddns.lexdev.systempro_api.dto;

import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.FiscalEmissionType;
import net.ddns.lexdev.systempro_api.repository.projection.SaleSummaryProjection;

public record FiscalDocumentSummaryResponseDto(
    Long id,
    Long number,
    FiscalEmissionType emissionType,
    FiscalDocumentStatus status,
    String accessKey,
    String protocol
) {
    public static FiscalDocumentSummaryResponseDto fromProjection(SaleSummaryProjection projection) {
        if (projection.getFiscalDocumentId() == null) {
            return null;
        }
        return new FiscalDocumentSummaryResponseDto(
            projection.getFiscalDocumentId(),
            projection.getFiscalNumber(),
            projection.getFiscalEmissionType(),
            projection.getFiscalStatus(),
            projection.getFiscalAccessKey(),
            projection.getFiscalProtocol()
        );
    }
}