package net.ddns.lexdev.systempro_api.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "refresh-token.cleanup")
public record RefreshTokenCleanupProperties(
        Duration fixedDelay,
        Duration initialDelay,
        Duration gracePeriod,
        int batchSize,
        int maxBatchesPerRun
) {

    public RefreshTokenCleanupProperties {
        if (fixedDelay == null || fixedDelay.isZero() || fixedDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "refresh-token.cleanup.fixed-delay deve ser positivo"
            );
        }

        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "refresh-token.cleanup.initial-delay não pode ser negativo"
            );
        }

        if (gracePeriod == null || gracePeriod.isNegative()) {
            throw new IllegalArgumentException(
                    "refresh-token.cleanup.grace-period não pode ser negativo"
            );
        }

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "refresh-token.cleanup.batch-size deve ser maior que zero"
            );
        }

        if (maxBatchesPerRun <= 0) {
            throw new IllegalArgumentException(
                    "refresh-token.cleanup.max-batches-per-run deve ser maior que zero"
            );
        }
    }

    public long gracePeriodSeconds() {
        return gracePeriod.toSeconds();
    }
}
