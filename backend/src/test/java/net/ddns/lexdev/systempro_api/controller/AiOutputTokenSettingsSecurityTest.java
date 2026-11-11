package net.ddns.lexdev.systempro_api.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import net.ddns.lexdev.systempro_api.config.JwtAuthenticationEntryPoint;
import net.ddns.lexdev.systempro_api.config.JwtAuthenticationFilter;
import net.ddns.lexdev.systempro_api.config.JwtTokenProvider;
import net.ddns.lexdev.systempro_api.config.SecurityConfig;
import net.ddns.lexdev.systempro_api.controller.support.AiOutputTokenSettingsController;
import net.ddns.lexdev.systempro_api.dto.support.AiOutputTokensDto;
import net.ddns.lexdev.systempro_api.licensing.LicenseEnforcementConfiguration;
import net.ddns.lexdev.systempro_api.licensing.LicenseStateService;
import net.ddns.lexdev.systempro_api.repository.UserRepository;
import net.ddns.lexdev.systempro_api.service.support.AiOutputTokenSettingsService;

@WebMvcTest(AiOutputTokenSettingsController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
         JwtAuthenticationEntryPoint.class, LicenseEnforcementConfiguration.class})
class AiOutputTokenSettingsSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AiOutputTokenSettingsService service;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean LicenseStateService licenseStateService;
    private static final String URL = "/support/configuration/ai-output-tokens";

    @ParameterizedTest
    @CsvSource({"SUPPORT,200", "ADMIN,403", "MANAGER,403", "USER,403"})
    void leituraExclusivaDoSuporte(String role, int expected) throws Exception {
        when(service.current()).thenReturn(new AiOutputTokensDto(600, 600, false));
        mvc.perform(get(URL).with(user("teste").roles(role)))
            .andExpect(status().is(expected));
    }

    @Test
    void escritaValidaComAuditoriaIdentificada() throws Exception {
        when(service.update(eq(900), eq("support")))
            .thenReturn(new AiOutputTokensDto(900, 600, true));
        mvc.perform(put(URL).with(user("support").roles("SUPPORT"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":900}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.effectiveValue").value(900))
            .andExpect(jsonPath("$.overridden").value(true));
    }

    @Test
    void naoAceitaValoresInvalidos() throws Exception {
        mvc.perform(put(URL).with(user("support").roles("SUPPORT"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":99999}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void adminNaoPodeEditarMesmoComPayloadValido() throws Exception {
        mvc.perform(put(URL).with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":900}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void restauracaoExclusivaDoSuporte() throws Exception {
        when(service.reset("support")).thenReturn(new AiOutputTokensDto(600, 600, false));
        mvc.perform(delete(URL).with(user("support").roles("SUPPORT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overridden").value(false));
        mvc.perform(delete(URL).with(user("admin").roles("ADMIN")))
            .andExpect(status().isForbidden());
    }
}
