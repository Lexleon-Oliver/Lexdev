package net.ddns.lexdev.systempro_api.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.ddns.lexdev.systempro_api.config.RefreshTokenCleanupProperties;
import net.ddns.lexdev.systempro_api.service.RefreshTokenCleanupService;

@Component
public class RefreshTokenCleanupScheduler {

    private static final Logger logger =
            LoggerFactory.getLogger(RefreshTokenCleanupScheduler.class);

    private final RefreshTokenCleanupService cleanupService;
    private final RefreshTokenCleanupProperties properties;

    public RefreshTokenCleanupScheduler(
            RefreshTokenCleanupService cleanupService,
            RefreshTokenCleanupProperties properties
    ) {
        this.cleanupService = cleanupService;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${refresh-token.cleanup.fixed-delay}",
            initialDelayString = "${refresh-token.cleanup.initial-delay}"
    )
    public void cleanupExpiredRefreshTokens() {
        int totalDeleted = 0;

        try {
            for (int batch = 0;
                 batch < properties.maxBatchesPerRun();
                 batch++) {

                int deleted = cleanupService.deleteExpiredBatch();

                totalDeleted += deleted;

                if (deleted < properties.batchSize()) {
                    break;
                }
            }

            if (totalDeleted > 0) {
                logger.info(
                        "Limpeza de refresh tokens concluída: {} registros expirados removidos.",
                        totalDeleted
                );
            }
        } catch (RuntimeException ex) {
            logger.error(
                    "Falha na limpeza automática dos refresh tokens expirados.",
                    ex
            );
        }
    }
}
