package net.ddns.lexdev.systempro_api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(RefreshTokenCleanupProperties.class)
public class RefreshTokenCleanupConfiguration {
}