package net.ddns.lexdev.systempro_api.licensing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import net.ddns.lexdev.systempro_api.config.JwtAuthenticationFilter;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(
    controllers = LicenseEnforcementWebMvcSliceTest.TestController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = JwtAuthenticationFilter.class
    )
)
class LicenseEnforcementWebMvcSliceTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void webMvcSliceDoesNotAutoRegisterCommercialLicenseFilter() {
        assertThat(applicationContext.getBeansOfType(LicenseEnforcementFilter.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(LicenseStateService.class)).isEmpty();
    }

    @RestController
    static class TestController {

        @GetMapping("/__licensing-slice-test")
        String ok() {
            return "ok";
        }
    }
}
