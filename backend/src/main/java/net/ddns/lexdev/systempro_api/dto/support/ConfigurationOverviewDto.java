package net.ddns.lexdev.systempro_api.dto.support;

import java.util.List;

/** Metadados e flags não sigilosas; nunca contém valores de secrets, JWT ou credenciais. */
public record ConfigurationOverviewDto(
    List<FeatureStatus> features,
    List<ManagementArea> areas
) {
    public record FeatureStatus(
        String key,
        String label,
        String category,
        boolean enabled,
        boolean requiresRestart
    ) {}

    public record ManagementArea(
        String name,
        String ownership,
        String location,
        String explanation
    ) {}
}
