package net.ddns.lexdev.systempro_api.controller;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.ddns.lexdev.systempro_api.config.JwtAuthenticationFilter;
import net.ddns.lexdev.systempro_api.dto.AuthResponse;
import net.ddns.lexdev.systempro_api.dto.LoginRequest;
import net.ddns.lexdev.systempro_api.dto.RefreshTokenRequest;
import net.ddns.lexdev.systempro_api.service.AuthService;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    // ---------------------------------------------------------
    // LOGIN
    // ---------------------------------------------------------

    @Test
    void login_ComDadosValidos_DeveRetornar200EAuthResponse()
            throws Exception {

        LoginRequest request =
                new LoginRequest("admin", "senha123");

        AuthResponse response =
                new AuthResponse("access-token", "refresh-token");

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("access-token"))
                .andExpect(jsonPath("$.refreshToken")
                        .value("refresh-token"))
                .andExpect(jsonPath("$.tokenType")
                        .value("Bearer"));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    void login_ComUsernameVazio_DeveRetornar400()
            throws Exception {

        String json = """
                {
                    "username": "",
                    "password": "senha123"
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void login_ComPasswordVazio_DeveRetornar400()
            throws Exception {

        String json = """
                {
                    "username": "admin",
                    "password": ""
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void login_ComBodyMalformado_DeveRetornar400()
            throws Exception {

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username":
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    // ---------------------------------------------------------
    // REFRESH TOKEN
    // ---------------------------------------------------------

    @Test
    void refresh_ComTokenValido_DeveRetornar200EAuthResponse()
            throws Exception {

        RefreshTokenRequest request =
                new RefreshTokenRequest("refresh-token");

        AuthResponse response =
                new AuthResponse(
                        "new-access-token",
                        "new-refresh-token"
                );

        when(authService.refreshToken(
                any(RefreshTokenRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken")
                        .value("new-refresh-token"))
                .andExpect(jsonPath("$.tokenType")
                        .value("Bearer"));

        verify(authService)
                .refreshToken(any(RefreshTokenRequest.class));
    }

    @Test
    void refresh_ComTokenVazio_DeveRetornar400()
            throws Exception {

        String json = """
                {
                    "refreshToken": ""
                }
                """;

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void refresh_SemRefreshToken_DeveRetornar400()
            throws Exception {

        String json = """
                {
                }
                """;

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    // ---------------------------------------------------------
    // LOGOUT
    // ---------------------------------------------------------

    @Test
    void logout_ComTokenValido_DeveRetornar204()
            throws Exception {

        String json = """
                {
                    "refreshToken": "refresh-token"
                }
                """;

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(authService).logout(
                any(RefreshTokenRequest.class));
    }

    @Test
    void logout_ComTokenVazio_DeveRetornar400()
            throws Exception {

        String json = """
                {
                    "refreshToken": ""
                }
                """;

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }
}
