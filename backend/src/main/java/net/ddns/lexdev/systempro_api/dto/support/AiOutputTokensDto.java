package net.ddns.lexdev.systempro_api.dto.support;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AiOutputTokensDto(int effectiveValue, int installationDefault, boolean overridden) {
    public record Update(@NotNull @Min(128) @Max(2048) Integer value) {}
}
