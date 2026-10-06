package net.ddns.lexdev.systempro_api.dto;

import java.time.Instant;

import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.FiscalEmissionType;

public record FiscalDocumentResponseDto(
    Long id, String model, int series, long number, String accessKey, FiscalEmissionType emissionType,
    FiscalDocumentStatus status, String protocol, String receiptNumber, String reason, Instant issuedAt,
    Instant canceledAt, String cancellationProtocol
) {
    public static FiscalDocumentResponseDto fromEntity(FiscalDocument d) {
        return new FiscalDocumentResponseDto(d.getId(), d.getModel(), d.getSeries(), d.getNumber(), d.getAccessKey(), d.getEmissionType(),
            d.getStatus(), d.getProtocol(), d.getReceiptNumber(), d.getReason(), d.getIssuedAt(), d.getCanceledAt(), d.getCancellationProtocol());
    }
}