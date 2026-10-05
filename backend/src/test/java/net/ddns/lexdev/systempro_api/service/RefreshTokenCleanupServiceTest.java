package net.ddns.lexdev.systempro_api.service;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import net.ddns.lexdev.systempro_api.config.RefreshTokenCleanupProperties;
import net.ddns.lexdev.systempro_api.repository.RefreshTokenRepository;

class RefreshTokenCleanupServiceTest {

    private RefreshTokenRepository repository;
    private RefreshTokenCleanupProperties properties;
    private RefreshTokenCleanupService service;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);

        properties = new RefreshTokenCleanupProperties(
                Duration.ofMinutes(15),
                Duration.ofMinutes(1),
                Duration.ofHours(1),
                1000,
                20
        );

        service = new RefreshTokenCleanupService(
                repository,
                properties
        );
    }

    @Test
    void deveExecutarUmBatchComOsParametrosConfigurados() {
        when(repository.deleteExpiredBatch(3600, 1000))
                .thenReturn(37);

        int deleted = service.deleteExpiredBatch();

        assertThat(deleted).isEqualTo(37);

        verify(repository)
                .deleteExpiredBatch(3600, 1000);

        verifyNoMoreInteractions(repository);
    }
}
