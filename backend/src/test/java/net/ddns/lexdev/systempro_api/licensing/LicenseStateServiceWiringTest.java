package net.ddns.lexdev.systempro_api.licensing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class LicenseStateServiceWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues(
            "systempro.license.enforcement-enabled=false",
            "systempro.license.instance-dir=/tmp/systempro-license-test",
            "systempro.license.cache-millis=0"
        );

    @Test
    void springCreatesLicensingBeansWithBoot4JsonMapper() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JsonMapper.class);
            assertThat(context).hasSingleBean(LicenseStateService.class);
            assertThat(context).hasSingleBean(LicenseEnforcementFilter.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({LicenseStateService.class, LicenseEnforcementConfiguration.class})
    static class TestConfig {

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }
}
