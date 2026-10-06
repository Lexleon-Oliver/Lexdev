package net.ddns.lexdev.systempro_api.dto;

import java.time.Instant;

import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;

public record FiscalServiceStatusDto(String statusCode, String reason, FiscalDocumentStatus mappedStatus, Instant checkedAt) {}
