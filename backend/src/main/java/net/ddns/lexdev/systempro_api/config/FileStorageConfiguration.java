package net.ddns.lexdev.systempro_api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    StorageProperties.class,
    ClamAvProperties.class,
    FiscalProperties.class
})
public class FileStorageConfiguration {
}
