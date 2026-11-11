package net.ddns.lexdev.systempro_api.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import net.ddns.lexdev.systempro_api.config.JwtAuthenticationEntryPoint;
import net.ddns.lexdev.systempro_api.config.JwtAuthenticationFilter;
import net.ddns.lexdev.systempro_api.config.JwtTokenProvider;
import net.ddns.lexdev.systempro_api.config.SecurityConfig;
import net.ddns.lexdev.systempro_api.controller.support.ConfigurationOverviewController;
import net.ddns.lexdev.systempro_api.dto.support.ConfigurationOverviewDto;
import net.ddns.lexdev.systempro_api.licensing.LicenseEnforcementConfiguration;
import net.ddns.lexdev.systempro_api.licensing.LicenseStateService;
import net.ddns.lexdev.systempro_api.repository.UserRepository;
import net.ddns.lexdev.systempro_api.service.support.ConfigurationOverviewService;

import static org.mockito.Mockito.when;

@WebMvcTest(ConfigurationOverviewController.class)
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    JwtAuthenticationEntryPoint.class,
    LicenseEnforcementConfiguration.class
})
class ConfigurationOverviewSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ConfigurationOverviewService service;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean LicenseStateService licenseStateService;

    @ParameterizedTest
    @CsvSource({"SUPPORT,200", "ADMIN,403", "MANAGER,403", "USER,403"})
    void apenasSuportePodeConsultar(String role, int expected) throws Exception {
        when(service.overview()).thenReturn(new ConfigurationOverviewDto(List.of(), List.of()));
        mockMvc.perform(get("/support/configuration").with(user("teste").roles(role)))
            .andExpect(status().is(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "POST", "PATCH", "DELETE"})
    void impedeAlteracaoViaHttp(String method) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), "/support/configuration")
                .with(user("teste").roles("SUPPORT")))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", containsString("GET")))
            .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void impedeCacheNoNavegador() throws Exception {
        when(service.overview()).thenReturn(new ConfigurationOverviewDto(List.of(), List.of()));
        mockMvc.perform(get("/support/configuration").with(user("teste").roles("SUPPORT")))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"));
    }
}
