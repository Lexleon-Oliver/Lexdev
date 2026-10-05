package net.ddns.lexdev.systempro_api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import net.ddns.lexdev.systempro_api.config.RefreshTokenCleanupProperties;
import net.ddns.lexdev.systempro_api.repository.RefreshTokenRepository;

@Service
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCleanupProperties properties;

    public RefreshTokenCleanupService(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenCleanupProperties properties
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteExpiredBatch() {
        return refreshTokenRepository.deleteExpiredBatch(
                properties.gracePeriodSeconds(),
                properties.batchSize()
        );
    }
}
