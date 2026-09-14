package net.ddns.lexdev.systempro_api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.config.JwtTokenProvider;
import net.ddns.lexdev.systempro_api.dto.ClientRequestDto;
import net.ddns.lexdev.systempro_api.dto.ClientResponseDto;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.UserRepository;
import net.ddns.lexdev.systempro_api.service.ClientService;

@WebMvcTest(ClientController.class)
@WithMockUser(username = "admin", roles = "ADMIN")
class ClientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientService service;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;


    @Test
    @DisplayName("Deve retornar lista paginada de clientes")
    void deveRetornarListaPaginadaDeClientes() throws Exception {

        ClientResponseDto client = new ClientResponseDto(
                1L,
                "FISICA",
                "João da Silva",
                null,
                "12345678900",
                "495493478",
                "joao@email.com",
                "31999999999",
                "65570-970",
                "Avenida Doutor Paulo Ramos",
                "100",
                null,
                "Conceição",
                "Araióses",
                "MA",
                true
        );

        PageImpl<ClientResponseDto> page =
                new PageImpl<>(
                        java.util.List.of(client),
                        PageRequest.of(0, 10),
                        1
                );

        when(service.findAll(any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].name").value("João da Silva"))
                .andExpect(jsonPath("$.content[0].cpfCnpj").value("12345678900"))
                .andExpect(jsonPath("$.content[0].active").value(true));

        verify(service).findAll(any(Pageable.class));
    }


    @Test
    @DisplayName("Deve encaminhar parâmetros de paginação ao service")
    void deveEncaminharParametrosDePaginacaoAoService() throws Exception {

        PageImpl<ClientResponseDto> page =
                new PageImpl<>(
                        java.util.List.of(),
                        PageRequest.of(1, 5),
                        0
                );

        when(service.findAll(any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(
                get("/clients")
                        .param("page", "1")
                        .param("size", "5")
        )
                .andExpect(status().isOk());

        verify(service).findAll(
                org.mockito.ArgumentMatchers.argThat(pageable ->
                        pageable.getPageNumber() == 1
                                && pageable.getPageSize() == 5
                )
        );
    }

        @Test
        @DisplayName("Deve retornar cliente pelo ID")
        void deveRetornarClientePeloId() throws Exception {

                ClientResponseDto client = new ClientResponseDto(
                        1L,
                        "PF",
                        "João da Silva",
                        null,
                        "12345678900",
                        "495493478",
                        "joao@email.com",
                        "31999999999",
                        "65570-970",
                        "Avenida Doutor Paulo Ramos",
                        "125",
                        null,
                        "Conceição",
                        "Araióses",
                        "MA",
                        true
                );

                when(service.findById(1L))
                        .thenReturn(client);

                mockMvc.perform(get("/clients/1"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(1))
                        .andExpect(jsonPath("$.tipoPessoa").value("PF"))
                        .andExpect(jsonPath("$.name").value("João da Silva"))
                        .andExpect(jsonPath("$.nomeFantasia").value(org.hamcrest.Matchers.nullValue()))
                        .andExpect(jsonPath("$.cpfCnpj").value("12345678900"))
                        .andExpect(jsonPath("$.rgIe").value("495493478"))
                        .andExpect(jsonPath("$.email")
                                .value("joao@email.com"))
                        .andExpect(jsonPath("$.phone").value("31999999999"))
                        .andExpect(jsonPath("$.cep").value("65570-970"))
                        .andExpect(jsonPath("$.logradouro")
                                .value("Avenida Doutor Paulo Ramos"))
                        .andExpect(jsonPath("$.numero").value("125"))
                        .andExpect(jsonPath("$.complemento").value(org.hamcrest.Matchers.nullValue()))
                        .andExpect(jsonPath("$.bairro").value("Conceição"))
                        .andExpect(jsonPath("$.cidade").value("Araióses"))
                        .andExpect(jsonPath("$.uf").value("MA"))
                        .andExpect(jsonPath("$.active").value(true));

                verify(service).findById(1L);
        }


        @Test
        @DisplayName("Deve retornar 404 quando cliente não existir")
        void deveRetornar404QuandoClienteNaoExistir() throws Exception {

                when(service.findById(999L))
                        .thenThrow(new EntityNotFoundException(
                                "Cliente não encontrado com o ID: 999"
                        ));

                mockMvc.perform(get("/clients/999"))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.error")
                                .value("Recurso Não Encontrado"))
                        .andExpect(jsonPath("$.message")
                                .value("Cliente não encontrado com o ID: 999"))
                        .andExpect(jsonPath("$.path")
                                .value("/clients/999"));

                verify(service).findById(999L);
        }

        @Test
        @DisplayName("Deve criar cliente e retornar 201 Created")
        void deveCriarCliente() throws Exception {

                ClientResponseDto response = new ClientResponseDto(
                        1L,
                        "PF",
                        "João da Silva",
                        null,
                        "12345678900",
                        "495493478",
                        "joao@email.com",
                        "31999999999",
                        "65570-970",
                        "Avenida Doutor Paulo Ramos",
                        "125",
                        null,
                        "Conceição",
                        "Araióses",
                        "MA",
                        true
                );

                when(service.create(any(ClientRequestDto.class)))
                        .thenReturn(response);

                String json = """
                        {
                                "tipoPessoa": "PF",
                                "name": "João da Silva",
                                "nomeFantasia": null,
                                "cpfCnpj": "12345678900",
                                "rgIe": "495493478",
                                "email": "joao@email.com",
                                "phone": "31999999999",
                                "cep": "65570-970",
                                "logradouro": "Avenida Doutor Paulo Ramos",
                                "numero": "125",
                                "complemento": null,
                                "bairro": "Conceição",
                                "cidade": "Araióses",
                                "uf": "MA",
                                "active": true
                        }
                        """;

                mockMvc.perform(
                        post("/clients")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                )
                        .andExpect(status().isCreated())
                        .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/clients/1")
        ))
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.id").value(1))
                        .andExpect(jsonPath("$.name")
                                .value("João da Silva"))
                        .andExpect(jsonPath("$.cpfCnpj")
                                .value("12345678900"))
                        .andExpect(jsonPath("$.active")
                                .value(true));

                verify(service).create(any(ClientRequestDto.class));
        }

        @Test
        @DisplayName("Deve retornar 400 quando os dados do cliente forem inválidos")
        void deveRetornar400QuandoDadosDoClienteForemInvalidos() throws Exception {

                String json = """
                        {
                                "tipoPessoa": "",
                                "name": "",
                                "nomeFantasia": null,
                                "cpfCnpj": "",
                                "rgIe": null,
                                "email": "email-invalido",
                                "phone": "",
                                "cep": null,
                                "logradouro": null,
                                "numero": null,
                                "complemento": null,
                                "bairro": null,
                                "cidade": null,
                                "uf": null,
                                "active": true
                        }
                        """;

                mockMvc.perform(
                        post("/clients")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.error")
                                .value("Erro de Validação"))
                        .andExpect(jsonPath("$.message")
                                .value("Um ou mais campos estão inválidos"))
                        .andExpect(jsonPath("$.path")
                                .value("/clients"))
                        .andExpect(jsonPath("$.fieldErrors").isArray())
                        .andExpect(jsonPath("$.fieldErrors").isNotEmpty());

                verify(service, never()).create(any(ClientRequestDto.class));
        }

        @Test
        @DisplayName("Deve retornar 400 quando CPF/CNPJ já estiver cadastrado")
        void deveRetornar400QuandoCpfCnpjJaEstiverCadastrado() throws Exception {
        when(service.create(any(ClientRequestDto.class)))
                .thenThrow(new BusinessException("Já existe um cliente ativo cadastrado com este CPF/CNPJ."));

        String json = """
                {
                        "tipoPessoa": "PF",
                        "name": "João da Silva",
                        "nomeFantasia": null,
                        "cpfCnpj": "12345678900",
                        "rgIe": "495493478",
                        "email": "joao@email.com",
                        "phone": "31999999999",
                        "cep": "65570-970",
                        "logradouro": "Avenida Doutor Paulo Ramos",
                        "numero": "125",
                        "complemento": null,
                        "bairro": "Conceição",
                        "cidade": "Araióses",
                        "uf": "MA",
                        "active": true
                }
                """;

        mockMvc.perform(
                post("/clients")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Regra de Negócio"))
        .andExpect(jsonPath("$.message").value("Já existe um cliente ativo cadastrado com este CPF/CNPJ."))
        .andExpect(jsonPath("$.path").value("/clients"));

        verify(service).create(any(ClientRequestDto.class));
        }

        @Test
        @DisplayName("Deve atualizar cliente e retornar 200 OK")
        void deveAtualizarCliente() throws Exception {

        ClientResponseDto response = new ClientResponseDto(
                1L,
                "PF",
                "João da Silva",
                null,
                "12345678900",
                "495493478",
                "joaoatualizado@email.com",
                "32999999999",
                "65570-970",
                "Rua Nova",
                "200",
                null,
                "Conceição",
                "Araióses",
                "MA",
                true
        );

        when(service.update(
                org.mockito.ArgumentMatchers.eq(1L),
                any(ClientRequestDto.class)
        )).thenReturn(response);

        String json = """
                {
                        "tipoPessoa": "PF",
                        "name": "João da Silva Atualizado",
                        "nomeFantasia": null,
                        "cpfCnpj": "12345678900",
                        "rgIe": "495493478",
                        "email": "joaoatualizado@email.com",
                        "phone": "32999999999",
                        "cep": "65570-970",
                        "logradouro": "Rua Nova",
                        "numero": "200",
                        "complemento": null,
                        "bairro": "Conceição",
                        "cidade": "Araióses",
                        "uf": "MA",
                        "active": true
                }
                """;

        mockMvc.perform(
                put("/clients/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name")
                        .value("João da Silva"))
                .andExpect(jsonPath("$.cpfCnpj")
                        .value("12345678900"))
                .andExpect(jsonPath("$.email")
                        .value("joaoatualizado@email.com"))
                .andExpect(jsonPath("$.phone")
                        .value("32999999999"))
                .andExpect(jsonPath("$.active").value(true));

        verify(service).update(
                org.mockito.ArgumentMatchers.eq(1L),
                any(ClientRequestDto.class)
        );
        }

        @Test
        @DisplayName("Deve retornar 400 quando dados do cliente forem inválidos no update")
        void deveRetornar400QuandoDadosDoClienteForemInvalidosNoUpdate() throws Exception {

                String json = """
                        {
                                "tipoPessoa": "",
                                "name": "",
                                "nomeFantasia": null,
                                "cpfCnpj": "",
                                "rgIe": null,
                                "email": "email-invalido",
                                "phone": "",
                                "cep": null,
                                "logradouro": null,
                                "numero": null,
                                "complemento": null,
                                "bairro": null,
                                "cidade": null,
                                "uf": null,
                                "active": true
                        }
                        """;

                mockMvc.perform(
                        put("/clients/1")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.error")
                                .value("Erro de Validação"))
                        .andExpect(jsonPath("$.message")
                                .value("Um ou mais campos estão inválidos"))
                        .andExpect(jsonPath("$.path").value("/clients/1"))
                        .andExpect(jsonPath("$.fieldErrors").isArray())
                        .andExpect(jsonPath("$.fieldErrors").isNotEmpty());

                verify(service, never())
                        .update(eq(1L), any(ClientRequestDto.class));
        }

        @Test
        @DisplayName("Deve retornar 404 ao atualizar cliente inexistente")
        void deveRetornar404AoAtualizarClienteInexistente() throws Exception {

                when(service.update(
                        eq(999L),
                        any(ClientRequestDto.class)
                )).thenThrow(
                        new EntityNotFoundException(
                                "Cliente não encontrado com o ID: 999"
                        )
                );

                String json = """
                        {
                                "tipoPessoa": "PF",
                                "name": "João da Silva",
                                "nomeFantasia": null,
                                "cpfCnpj": "12345678900",
                                "rgIe": "495493478",
                                "email": "joao@email.com",
                                "phone": "31999999999",
                                "cep": null,
                                "logradouro": null,
                                "numero": null,
                                "complemento": null,
                                "bairro": null,
                                "cidade": null,
                                "uf": null,
                                "active": true
                        }
                        """;

                mockMvc.perform(
                        put("/clients/999")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                )
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.error")
                                .value("Recurso Não Encontrado"))
                        .andExpect(jsonPath("$.message")
                                .value("Cliente não encontrado com o ID: 999"))
                        .andExpect(jsonPath("$.path")
                                .value("/clients/999"));

                verify(service).update(
                        eq(999L),
                        any(ClientRequestDto.class)
                );
        }

        @Test
        @DisplayName("Deve retornar 400 ao atualizar com CPF/CNPJ pertencente a outro cliente")
        void deveRetornar400AoAtualizarComCpfCnpjDeOutroCliente() throws Exception {
                when(service.update(eq(1L), any(ClientRequestDto.class)))
                        .thenThrow(new BusinessException("CPF/CNPJ já cadastrado para outro cliente."));

                String json = """
                        {
                                "tipoPessoa": "PF",
                                "name": "João da Silva",
                                "nomeFantasia": null,
                                "cpfCnpj": "12345678900",
                                "rgIe": "495493478",
                                "email": "joao@email.com",
                                "phone": "32999999999",
                                "cep": "65570-970",
                                "logradouro": "Avenida Doutor Paulo Ramos",
                                "numero": "100",
                                "complemento": null,
                                "bairro": "Conceição",
                                "cidade": "Araióses",
                                "uf": "MA",
                                "active": true
                        }
                        """;

                mockMvc.perform(
                        put("/clients/1")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.error").value("Regra de Negócio"))
                        .andExpect(jsonPath("$.message").value("CPF/CNPJ já cadastrado para outro cliente."))
                        .andExpect(jsonPath("$.path").value("/clients/1"));

                verify(service).update(eq(1L), any(ClientRequestDto.class));
        }

        @Test
        @DisplayName("Deve excluir cliente e retornar 204 No Content")
        void deveExcluirCliente() throws Exception {

                mockMvc.perform(
                        delete("/clients/1")
                                .with(csrf())
                )
                        .andExpect(status().isNoContent())
                        .andExpect(content().string(""));

                verify(service).delete(1L);
        }


        @Test
        @DisplayName("Deve retornar 404 ao excluir cliente inexistente")
        void deveRetornar404AoExcluirClienteInexistente() throws Exception {

                doThrow(
                        new EntityNotFoundException(
                                "Cliente não encontrado com o ID: 999"
                        )
                ).when(service).delete(999L);

                mockMvc.perform(
                        delete("/clients/999")
                                .with(csrf())
                )
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.error")
                                .value("Recurso Não Encontrado"))
                        .andExpect(jsonPath("$.message")
                                .value("Cliente não encontrado com o ID: 999"))
                        .andExpect(jsonPath("$.path")
                                .value("/clients/999"));

                verify(service).delete(999L);
        }







}


