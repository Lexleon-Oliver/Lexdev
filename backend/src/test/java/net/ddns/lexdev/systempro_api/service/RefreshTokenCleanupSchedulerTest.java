package net.ddns.lexdev.systempro_api.service;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.ddns.lexdev.systempro_api.config.RefreshTokenCleanupProperties;
import net.ddns.lexdev.systempro_api.schedule.RefreshTokenCleanupScheduler;

class RefreshTokenCleanupSchedulerTest {

    private RefreshTokenCleanupService cleanupService;
    private RefreshTokenCleanupProperties properties;
    private RefreshTokenCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        cleanupService = mock(RefreshTokenCleanupService.class);

        properties = new RefreshTokenCleanupProperties(
                Duration.ofMinutes(15),
                Duration.ofMinutes(1),
                Duration.ofHours(1),
                1000,
                20
        );

        scheduler = new RefreshTokenCleanupScheduler(
                cleanupService,
                properties
        );
    }

    @Test
    void deveContinuarProcessandoEnquantoBatchEstiverCheio() {
        when(cleanupService.deleteExpiredBatch())
                .thenReturn(1000)
                .thenReturn(1000)
                .thenReturn(250);

        scheduler.cleanupExpiredRefreshTokens();

        verify(cleanupService, times(3))
                .deleteExpiredBatch();
    }

    @Test
    void devePararQuandoBatchNaoEstiverCheio() {
        when(cleanupService.deleteExpiredBatch())
                .thenReturn(250);

        scheduler.cleanupExpiredRefreshTokens();

        verify(cleanupService, times(1))
                .deleteExpiredBatch();
    }

    @Test
    void naoDeveExecutarMaisQueMaximoDeBatches() {
        when(cleanupService.deleteExpiredBatch())
                .thenReturn(1000);

        scheduler.cleanupExpiredRefreshTokens();

        verify(cleanupService, times(20))
                .deleteExpiredBatch();
    }
}
