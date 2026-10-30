package net.ddns.lexdev.systempro_api.licensing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class LicenseEnforcementConfiguration {

    @Bean
    LicenseEnforcementFilter licenseEnforcementFilter(
        LicenseStateService licenseStateService,
        JsonMapper objectMapper
    ) {
        return new LicenseEnforcementFilter(licenseStateService, objectMapper);
    }
}
