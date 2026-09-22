package net.ddns.lexdev.systempro_api.controller;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.ddns.lexdev.systempro_api.config.JwtTokenProvider;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.integration.IntegrationTestBase;
import net.ddns.lexdev.systempro_api.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String accessToken;

    @BeforeEach
    void setUp() {

        String username = "test-" + UUID.randomUUID();

        User user = new User(
            username,
            "Usuário de Teste",
            username + "@systempro.test",
            passwordEncoder.encode("Test@123"),
            "ROLE_ADMIN"
        );

        userRepository.saveAndFlush(user);

        accessToken = jwtTokenProvider
            .generateAccessToken(username)
            .token();
    }

    @Test
    void naoDevePermitirConsultaDeClientesSemToken() throws Exception {

        mockMvc.perform(
                get("/clients")
            )
            .andExpect(status().is4xxClientError());
    }

    @Test
    void devePermitirConsultaDeClientesComTokenValido() throws Exception {

        mockMvc.perform(
                get("/clients")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
                    .param("page", "0")
                    .param("size", "10")
            )
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.number").value(0))
            .andExpect(jsonPath("$.size").value(10));
    }
}
