package net.ddns.lexdev.systempro_api.dto.dashboard;

public record DashboardInsightResponseDto(
    Status status,
    String insight
) {

    public enum Status {
        GENERATED,
        DISABLED,
        UNAVAILABLE
    }
}
