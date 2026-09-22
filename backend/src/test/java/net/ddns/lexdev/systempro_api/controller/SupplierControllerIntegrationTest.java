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
public class SupplierControllerIntegrationTest extends IntegrationTestBase {

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
    void naoDevePermitirConsultaDeFornecedoresSemToken() throws Exception {

        mockMvc.perform(
                get("/suppliers")
            )
            .andExpect(status().is4xxClientError());
    }

    @Test
    void devePermitirConsultaDeForenecedoresComTokenValido() throws Exception {

        mockMvc.perform(
                get("/suppliers")
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
    void deveCriarFornecedorComSucesso() throws Exception {

        String sufixo = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);

        String nome = "Fornecedor Integração " + sufixo;
        String cnpj = gerarCnpjValido();

        String json = """
            {
                "person": {
                    "cpfCnpj": "%s",
                    "tipoPessoa": "PJ",
                    "name": "%s"
                },
                "individual": null,
                "legalEntity": {
                    "nomeFantasia": "Fornecedor %s",
                    "inscricaoEstadual": "123456789"
                },
                "contacts": [],
                "addresses": [],
                "condicaoPagamentoPadrao": "30 DIAS",
                "prazoEntregaDias": 15,
                "valorMinimoPedido": 1000.00,
                "categoria": "Tecnologia",
                "observacoesComerciais": "Fornecedor de integração",
                "bankDetails": null,
                "contatos": [],
                "documentos": [],
                "active": true
            }
            """.formatted(cnpj, nome, sufixo);

        mockMvc.perform(
                post("/suppliers")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json)
            )
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.person.name").value(nome))
            .andExpect(jsonPath("$.person.cpfCnpj").value(cnpj))
            .andExpect(jsonPath("$.person.tipoPessoa").value("PJ"))
            .andExpect(jsonPath("$.legalEntity.nomeFantasia")
                .value("Fornecedor " + sufixo))
            .andExpect(jsonPath("$.legalEntity.inscricaoEstadual")
                .value("123456789"))
            .andExpect(jsonPath("$.condicaoPagamentoPadrao")
                .value("30 DIAS"))
            .andExpect(jsonPath("$.prazoEntregaDias").value(15))
            .andExpect(jsonPath("$.valorMinimoPedido")
                .value(1000.00))
            .andExpect(jsonPath("$.categoria").value("Tecnologia"))
            .andExpect(jsonPath("$.active").value(true));
    }

    private String gerarCnpjValido() {

        String base = UUID.randomUUID()
            .toString()
            .replaceAll("\\D", "")
            .substring(0, 8);

        base += "0001";

        int soma = 0;
        int[] pesosPrimeiro = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        for (int i = 0; i < 12; i++) {
            soma += Character.getNumericValue(base.charAt(i))
                * pesosPrimeiro[i];
        }

        int primeiroDigito = 11 - (soma % 11);

        if (primeiroDigito >= 10) {
            primeiroDigito = 0;
        }

        String parcial = base + primeiroDigito;

        soma = 0;
        int[] pesosSegundo = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        for (int i = 0; i < 13; i++) {
            soma += Character.getNumericValue(parcial.charAt(i))
                * pesosSegundo[i];
        }

        int segundoDigito = 11 - (soma % 11);

        if (segundoDigito >= 10) {
            segundoDigito = 0;
        }

        return parcial + segundoDigito;
    }

    @Test
    void deveBuscarFornecedorPorIdComSucesso() throws Exception {

        String sufixo = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);

        String nome = "Fornecedor Consulta " + sufixo;
        String cnpj = gerarCnpjValido();

        String json = """
            {
                "person": {
                    "cpfCnpj": "%s",
                    "tipoPessoa": "PJ",
                    "name": "%s"
                },
                "individual": null,
                "legalEntity": {
                    "nomeFantasia": "Fornecedor %s",
                    "inscricaoEstadual": "987654321"
                },
                "contacts": [],
                "addresses": [],
                "condicaoPagamentoPadrao": "28 DIAS",
                "prazoEntregaDias": 20,
                "valorMinimoPedido": 500.00,
                "categoria": "Materiais",
                "observacoesComerciais": "Fornecedor para teste de consulta",
                "bankDetails": null,
                "contatos": [],
                "documentos": [],
                "active": true
            }
            """.formatted(cnpj, nome, sufixo);

        MvcResult resultadoCriacao = mockMvc.perform(
                post("/suppliers")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json)
            )
            .andExpect(status().isCreated())
            .andReturn();

        String responseCriacao = resultadoCriacao
            .getResponse()
            .getContentAsString();

        Number idExtraido = JsonPath.read(
            responseCriacao,
            "$.id"
        );

        Long id = idExtraido.longValue();

        mockMvc.perform(
                get("/suppliers/{id}", id)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.person.name").value(nome))
            .andExpect(jsonPath("$.person.cpfCnpj").value(cnpj))
            .andExpect(jsonPath("$.person.tipoPessoa").value("PJ"))
            .andExpect(jsonPath("$.legalEntity.nomeFantasia")
                .value("Fornecedor " + sufixo))
            .andExpect(jsonPath("$.condicaoPagamentoPadrao")
                .value("28 DIAS"))
            .andExpect(jsonPath("$.prazoEntregaDias").value(20))
            .andExpect(jsonPath("$.valorMinimoPedido")
                .value(500.00))
            .andExpect(jsonPath("$.categoria").value("Materiais"))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void naoDeveBuscarFornecedorInexistente() throws Exception {

        Long idInexistente = Long.MAX_VALUE;

        mockMvc.perform(
                get("/suppliers/{id}", idInexistente)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
            )
            .andExpect(status().isNotFound());
    }

    @Test
    void deveAtualizarFornecedorComSucesso() throws Exception {

        String sufixo = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);

        String cnpj = gerarCnpjValido();

        String nomeOriginal = "Fornecedor Original " + sufixo;
        String nomeAtualizado = "Fornecedor Atualizado " + sufixo;

        String jsonCriacao = """
            {
                "person": {
                    "cpfCnpj": "%s",
                    "tipoPessoa": "PJ",
                    "name": "%s"
                },
                "individual": null,
                "legalEntity": {
                    "nomeFantasia": "Fantasia Original %s",
                    "inscricaoEstadual": "123456789"
                },
                "contacts": [],
                "addresses": [],
                "condicaoPagamentoPadrao": "30 DIAS",
                "prazoEntregaDias": 15,
                "valorMinimoPedido": 1000.00,
                "categoria": "Tecnologia",
                "observacoesComerciais": "Dados originais",
                "bankDetails": null,
                "contatos": [],
                "documentos": [],
                "active": true
            }
            """.formatted(cnpj, nomeOriginal, sufixo);

        MvcResult resultadoCriacao = mockMvc.perform(
                post("/suppliers")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonCriacao)
            )
            .andExpect(status().isCreated())
            .andReturn();

        String responseCriacao = resultadoCriacao
            .getResponse()
            .getContentAsString();

        Number idExtraido = JsonPath.read(
            responseCriacao,
            "$.id"
        );

        Long id = idExtraido.longValue();

        String jsonAtualizacao = """
            {
                "person": {
                    "cpfCnpj": "%s",
                    "tipoPessoa": "PJ",
                    "name": "%s"
                },
                "individual": null,
                "legalEntity": {
                    "nomeFantasia": "Fantasia Atualizada %s",
                    "inscricaoEstadual": "987654321"
                },
                "contacts": [],
                "addresses": [],
                "condicaoPagamentoPadrao": "45 DIAS",
                "prazoEntregaDias": 30,
                "valorMinimoPedido": 2500.00,
                "categoria": "Equipamentos",
                "observacoesComerciais": "Dados atualizados",
                "bankDetails": null,
                "contatos": [],
                "documentos": [],
                "active": true
            }
            """.formatted(cnpj, nomeAtualizado, sufixo);

        mockMvc.perform(
                put("/suppliers/{id}", id)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonAtualizacao)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.person.name")
                .value(nomeAtualizado))
            .andExpect(jsonPath("$.person.cpfCnpj")
                .value(cnpj))
            .andExpect(jsonPath("$.legalEntity.nomeFantasia")
                .value("Fantasia Atualizada " + sufixo))
            .andExpect(jsonPath("$.legalEntity.inscricaoEstadual")
                .value("987654321"))
            .andExpect(jsonPath("$.condicaoPagamentoPadrao")
                .value("45 DIAS"))
            .andExpect(jsonPath("$.prazoEntregaDias")
                .value(30))
            .andExpect(jsonPath("$.valorMinimoPedido")
                .value(2500.00))
            .andExpect(jsonPath("$.categoria")
                .value("Equipamentos"))
            .andExpect(jsonPath("$.observacoesComerciais")
                .value("Dados atualizados"))
            .andExpect(jsonPath("$.active")
                .value(true));
    }

    @Test
    void naoDeveAtualizarFornecedorInexistente() throws Exception {

        Long idInexistente = Long.MAX_VALUE;

        String json = """
            {
                "person": {
                    "cpfCnpj": "12345678000195",
                    "tipoPessoa": "PJ",
                    "name": "Fornecedor Inexistente"
                },
                "individual": null,
                "legalEntity": {
                    "nomeFantasia": "Fornecedor Inexistente",
                    "inscricaoEstadual": "123456789"
                },
                "contacts": [],
                "addresses": [],
                "condicaoPagamentoPadrao": "30 DIAS",
                "prazoEntregaDias": 10,
                "valorMinimoPedido": 100.00,
                "categoria": "Teste",
                "observacoesComerciais": "Teste",
                "bankDetails": null,
                "contatos": [],
                "documentos": [],
                "active": true
            }
            """;

        mockMvc.perform(
                put("/suppliers/{id}", idInexistente)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json)
            )
            .andExpect(status().isNotFound());
    }

    @Test
    void deveExcluirFornecedorLogicamenteComSucesso() throws Exception {

        String sufixo = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);

        String cnpj = gerarCnpjValido();
        String nome = "Fornecedor Para Excluir " + sufixo;

        String json = """
            {
                "person": {
                    "cpfCnpj": "%s",
                    "tipoPessoa": "PJ",
                    "name": "%s"
                },
                "individual": null,
                "legalEntity": {
                    "nomeFantasia": "Fornecedor %s",
                    "inscricaoEstadual": "123456789"
                },
                "contacts": [],
                "addresses": [],
                "condicaoPagamentoPadrao": "30 DIAS",
                "prazoEntregaDias": 15,
                "valorMinimoPedido": 1000.00,
                "categoria": "Tecnologia",
                "observacoesComerciais": "Fornecedor para exclusão",
                "bankDetails": null,
                "contatos": [],
                "documentos": [],
                "active": true
            }
            """.formatted(cnpj, nome, sufixo);

        MvcResult resultadoCriacao = mockMvc.perform(
                post("/suppliers")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json)
            )
            .andExpect(status().isCreated())
            .andReturn();

        String responseCriacao = resultadoCriacao
            .getResponse()
            .getContentAsString();

        Number idExtraido = JsonPath.read(
            responseCriacao,
            "$.id"
        );

        Long id = idExtraido.longValue();

        // Exclusão lógica
        mockMvc.perform(
                delete("/suppliers/{id}", id)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
            )
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        // O fornecedor inativo não deve mais ser encontrado
        mockMvc.perform(
                get("/suppliers/{id}", id)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
            )
            .andExpect(status().isNotFound());
    }

    

}
