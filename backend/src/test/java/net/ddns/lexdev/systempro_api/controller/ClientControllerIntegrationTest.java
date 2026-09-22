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
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

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

    @Test
    void deveCriarClienteComSucesso() throws Exception {

        String sufixo = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);

        String nome = "Cliente Integração " + sufixo;
        String cpf = gerarCpfValido();

        String json = """
            {
                "person": {
                    "cpfCnpj": "%s",
                    "tipoPessoa": "PF",
                    "name": "%s"
                },
                "individual": {
                    "rg": "RG-%s"
                },
                "legalEntity": null,
                "contacts": [],
                "addresses": [],
                "active": true
            }
            """.formatted(cpf, nome, sufixo);

        mockMvc.perform(post("/clients")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.person.name").value(nome))
            .andExpect(jsonPath("$.person.cpfCnpj").value(cpf))
            .andExpect(jsonPath("$.person.tipoPessoa").value("PF"))
            .andExpect(jsonPath("$.active").value(true));
    }

    private String gerarCpfValido() {

        String base = UUID.randomUUID()
            .toString()
            .replaceAll("\\D", "")
            .substring(0, 9);

        int soma = 0;

        for (int i = 0; i < 9; i++) {
            soma += Character.getNumericValue(base.charAt(i)) * (10 - i);
        }

        int primeiroDigito = 11 - (soma % 11);

        if (primeiroDigito >= 10) {
            primeiroDigito = 0;
        }

        String parcial = base + primeiroDigito;

        soma = 0;

        for (int i = 0; i < 10; i++) {
            soma += Character.getNumericValue(parcial.charAt(i)) * (11 - i);
        }

        int segundoDigito = 11 - (soma % 11);

        if (segundoDigito >= 10) {
            segundoDigito = 0;
        }

        return base + primeiroDigito + segundoDigito;
    }

    @Test
    void deveBuscarClientePorIdComSucesso() throws Exception {

        String sufixo = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);

        String nome = "Cliente Consulta " + sufixo;
        String cpf = gerarCpfValido();

        String json = """
            {
                "person": {
                    "cpfCnpj": "%s",
                    "tipoPessoa": "PF",
                    "name": "%s"
                },
                "individual": {
                    "rg": "RG-%s"
                },
                "legalEntity": null,
                "contacts": [],
                "addresses": [],
                "active": true
            }
            """.formatted(cpf, nome, sufixo);

        MvcResult resultadoCriacao = mockMvc.perform(post("/clients")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andReturn();

        String response = resultadoCriacao
            .getResponse()
            .getContentAsString();

        Long id = ((Number) JsonPath.read(response, "$.id")).longValue();

        mockMvc.perform(get("/clients/{id}", id)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.person.name").value(nome))
            .andExpect(jsonPath("$.person.cpfCnpj").value(cpf))
            .andExpect(jsonPath("$.person.tipoPessoa").value("PF"))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void naoDeveBuscarClienteInexistente() throws Exception {

        Long idInexistente = Long.MAX_VALUE;

        mockMvc.perform(get("/clients/{id}", idInexistente)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken
                ))
            .andExpect(status().isNotFound());
    }

    @Test
    void deveAtualizarClienteComSucesso() throws Exception {

        String sufixo = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);

        String cpf = gerarCpfValido();
        String nomeOriginal = "Cliente Original " + sufixo;
        String nomeAtualizado = "Cliente Atualizado " + sufixo;

        String jsonCriacao = """
            {
                "person": {
                    "cpfCnpj": "%s",
                    "tipoPessoa": "PF",
                    "name": "%s"
                },
                "individual": {
                    "rg": "RG-ORIGINAL-%s"
                },
                "legalEntity": null,
                "contacts": [],
                "addresses": [],
                "active": true
            }
            """.formatted(cpf, nomeOriginal, sufixo);

        MvcResult resultadoCriacao = mockMvc.perform(post("/clients")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonCriacao))
            .andExpect(status().isCreated())
            .andReturn();

        String responseCriacao = resultadoCriacao
            .getResponse()
            .getContentAsString();

        Number idExtraido = JsonPath.read(responseCriacao, "$.id");
        Long id = idExtraido.longValue();

        String jsonAtualizacao = """
            {
                "person": {
                    "cpfCnpj": "%s",
                    "tipoPessoa": "PF",
                    "name": "%s"
                },
                "individual": {
                    "rg": "RG-ATUALIZADO-%s"
                },
                "legalEntity": null,
                "contacts": [],
                "addresses": [],
                "active": true
            }
            """.formatted(cpf, nomeAtualizado, sufixo);

        mockMvc.perform(put("/clients/{id}", id)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonAtualizacao))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.person.name").value(nomeAtualizado))
            .andExpect(jsonPath("$.person.cpfCnpj").value(cpf))
            .andExpect(jsonPath("$.individual.rg")
                .value("RG-ATUALIZADO-" + sufixo))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void naoDeveAtualizarClienteInexistente() throws Exception {

        Long idInexistente = Long.MAX_VALUE;

        String json = """
            {
                "person": {
                    "cpfCnpj": "529.982.247-25",
                    "tipoPessoa": "PF",
                    "name": "Cliente Inexistente"
                },
                "individual": {
                    "rg": "RG-TESTE"
                },
                "legalEntity": null,
                "contacts": [],
                "addresses": [],
                "active": true
            }
            """;

        mockMvc.perform(put("/clients/{id}", idInexistente)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isNotFound());
    }

    @Test
    void deveExcluirClienteLogicamenteComSucesso() throws Exception {

        String sufixo = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);

        String cpf = gerarCpfValido();
        String nome = "Cliente Para Excluir " + sufixo;

        String json = """
            {
                "person": {
                    "cpfCnpj": "%s",
                    "tipoPessoa": "PF",
                    "name": "%s"
                },
                "individual": {
                    "rg": "RG-%s"
                },
                "legalEntity": null,
                "contacts": [],
                "addresses": [],
                "active": true
            }
            """.formatted(cpf, nome, sufixo);

        MvcResult resultadoCriacao = mockMvc.perform(post("/clients")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andReturn();

        String responseCriacao = resultadoCriacao
            .getResponse()
            .getContentAsString();

        Number idExtraido = JsonPath.read(responseCriacao, "$.id");
        Long id = idExtraido.longValue();

        // Executa a exclusão lógica
        mockMvc.perform(delete("/clients/{id}", id)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken
                ))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        // Confirma que o cliente não é mais encontrado
        mockMvc.perform(get("/clients/{id}", id)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken
                ))
            .andExpect(status().isNotFound());
    }
}
