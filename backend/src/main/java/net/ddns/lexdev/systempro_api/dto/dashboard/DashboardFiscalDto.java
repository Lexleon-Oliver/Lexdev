package net.ddns.lexdev.systempro_api.dto.dashboard;

public record DashboardFiscalDto(
    long pending,
    long awaitingAuthorization,
    long pendingConsultation,
    long offlineContingency,
    long pendingCancellation
) {}