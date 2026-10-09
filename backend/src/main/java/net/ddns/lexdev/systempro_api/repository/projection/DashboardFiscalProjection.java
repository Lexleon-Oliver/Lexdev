package net.ddns.lexdev.systempro_api.repository.projection;

public interface DashboardFiscalProjection {
    Long getAwaitingAuthorization();
    Long getPendingConsultation();
    Long getOfflineContingency();
    Long getPendingCancellation();
}