package net.ddns.lexdev.systempro_api.fiscal;

import java.time.Instant;

import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;

public record NfceIssueResult(
    FiscalDocumentStatus status,
    String accessKey,
    String signedXml,
    String responseXml,
    String protocol,
    String receiptNumber,
    String reason,
    Instant issuedAt
) {}
