package net.ddns.lexdev.systempro_api.controller;

import java.util.List;

import org.hamcrest.Matchers;
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
import net.ddns.lexdev.systempro_api.dto.IndividualPersonResponseDto;
import net.ddns.lexdev.systempro_api.dto.PersonAddressResponseDto;
import net.ddns.lexdev.systempro_api.dto.PersonContactResponseDto;
import net.ddns.lexdev.systempro_api.dto.PersonResponseDto;
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

    private ClientResponseDto createClientResponse() {

        PersonResponseDto person = new PersonResponseDto(
            10L,
            "PF",
            "João da Silva",
            "12345678900"
        );

        IndividualPersonResponseDto individual =
            new IndividualPersonResponseDto(
                20L,
                "495493478"
            );

        List<PersonContactResponseDto> contacts = List.of(
            new PersonContactResponseDto(
                30L,
                "EMAIL",
                "joao@email.com",
                true,
                null
            ),
            new PersonContactResponseDto(
                31L,
                "WHATSAPP",
                "31999999999",
                false,
                null
            )
        );

        List<PersonAddressResponseDto> addresses = List.of(
            new PersonAddressResponseDto(
                40L,
                "RESIDENCIAL",
                "65570970",
                "Avenida Doutor Paulo Ramos",
                "125",
                null,
                "Conceição",
                "Araióses",
                "MA",
                true
            )
        );

        return new ClientResponseDto(
            1L,
            person,
            individual,
            null,
            contacts,
            addresses,
            true
        );
    }

    @Test
    @DisplayName("Deve retornar lista paginada de clientes")
    void deveRetornarListaPaginadaDeClientes() throws Exception {

        ClientResponseDto client = createClientResponse();

        PageImpl<ClientResponseDto> page = new PageImpl<>(
            List.of(client),
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

            .andExpect(jsonPath("$.content[0].person.id").value(10))
            .andExpect(jsonPath("$.content[0].person.tipoPessoa").value("PF"))
            .andExpect(jsonPath("$.content[0].person.name")
                .value("João da Silva"))
            .andExpect(jsonPath("$.content[0].person.cpfCnpj")
                .value("12345678900"))

            .andExpect(jsonPath("$.content[0].individual.id").value(20))
            .andExpect(jsonPath("$.content[0].individual.rg")
                .value("495493478"))

            .andExpect(jsonPath("$.content[0].legalEntity")
                .value(Matchers.nullValue()))

            .andExpect(jsonPath("$.content[0].contacts").isArray())
            .andExpect(jsonPath("$.content[0].contacts.length()").value(2))

            .andExpect(jsonPath("$.content[0].addresses").isArray())
            .andExpect(jsonPath("$.content[0].addresses.length()").value(1))

            .andExpect(jsonPath("$.content[0].active").value(true));

        verify(service).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Deve encaminhar parâmetros de paginação ao service")
    void deveEncaminharParametrosDePaginacaoAoService() throws Exception {

        PageImpl<ClientResponseDto> page = new PageImpl<>(
            List.of(),
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
            org.mockito.ArgumentMatchers.argThat(
                pageable ->
                    pageable.getPageNumber() == 1 &&
                    pageable.getPageSize() == 5
            )
        );
    }

    @Test
    @DisplayName("Deve retornar cliente pelo ID")
    void deveRetornarClientePeloId() throws Exception {

        ClientResponseDto client = createClientResponse();

        when(service.findById(1L))
            .thenReturn(client);

        mockMvc.perform(get("/clients/1"))

            .andExpect(status().isOk())

            .andExpect(jsonPath("$.id").value(1))

            .andExpect(jsonPath("$.person.id").value(10))
            .andExpect(jsonPath("$.person.tipoPessoa").value("PF"))
            .andExpect(jsonPath("$.person.name")
                .value("João da Silva"))
            .andExpect(jsonPath("$.person.cpfCnpj")
                .value("12345678900"))

            .andExpect(jsonPath("$.individual.id").value(20))
            .andExpect(jsonPath("$.individual.rg")
                .value("495493478"))

            .andExpect(jsonPath("$.legalEntity")
                .value(Matchers.nullValue()))

            .andExpect(jsonPath("$.contacts[0].id").value(30))
            .andExpect(jsonPath("$.contacts[0].type").value("EMAIL"))
            .andExpect(jsonPath("$.contacts[0].value")
                .value("joao@email.com"))
            .andExpect(jsonPath("$.contacts[0].principal").value(true))

            .andExpect(jsonPath("$.addresses[0].id").value(40))
            .andExpect(jsonPath("$.addresses[0].type")
                .value("RESIDENCIAL"))
            .andExpect(jsonPath("$.addresses[0].cep")
                .value("65570970"))
            .andExpect(jsonPath("$.addresses[0].logradouro")
                .value("Avenida Doutor Paulo Ramos"))
            .andExpect(jsonPath("$.addresses[0].numero")
                .value("125"))
            .andExpect(jsonPath("$.addresses[0].bairro")
                .value("Conceição"))
            .andExpect(jsonPath("$.addresses[0].cidade")
                .value("Araióses"))
            .andExpect(jsonPath("$.addresses[0].uf")
                .value("MA"))
            .andExpect(jsonPath("$.addresses[0].principal")
                .value(true))

            .andExpect(jsonPath("$.active").value(true));

        verify(service).findById(1L);
    }

    @Test
    @DisplayName("Deve retornar 404 quando cliente não existir")
    void deveRetornar404QuandoClienteNaoExistir() throws Exception {

        when(service.findById(999L))
            .thenThrow(
                new EntityNotFoundException(
                    "Cliente não encontrado com o ID: 999"
                )
            );

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

        ClientResponseDto response = createClientResponse();

        when(service.create(any(ClientRequestDto.class)))
            .thenReturn(response);

        String json = """
            {
                "person": {
                    "tipoPessoa": "PF",
                    "name": "João da Silva",
                    "cpfCnpj": "12345678900"
                },
                "individual": {
                    "rg": "495493478"
                },
                "legalEntity": null,
                "contacts": [
                    {
                        "type": "EMAIL",
                        "value": "joao@email.com",
                        "principal": true
                    },
                    {
                        "type": "WHATSAPP",
                        "value": "31999999999",
                        "principal": false
                    }
                ],
                "addresses": [
                    {
                        "type": "RESIDENCIAL",
                        "cep": "65570-970",
                        "logradouro": "Avenida Doutor Paulo Ramos",
                        "numero": "125",
                        "complemento": null,
                        "bairro": "Conceição",
                        "cidade": "Araióses",
                        "uf": "MA",
                        "principal": true
                    }
                ],
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

            .andExpect(
                header().string(
                    "Location",
                    Matchers.endsWith("/clients/1")
                )
            )

            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON
                )
            )

            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.person.id").value(10))
            .andExpect(jsonPath("$.person.name")
                .value("João da Silva"))
            .andExpect(jsonPath("$.person.cpfCnpj")
                .value("12345678900"))
            .andExpect(jsonPath("$.individual.rg")
                .value("495493478"))
            .andExpect(jsonPath("$.active").value(true));

        verify(service).create(any(ClientRequestDto.class));
    }

    @Test
    @DisplayName("Deve retornar 400 quando os dados do cliente forem inválidos")
    void deveRetornar400QuandoDadosDoClienteForemInvalidos()
        throws Exception {

        String json = """
            {
                "person": {
                    "tipoPessoa": "",
                    "name": "",
                    "cpfCnpj": ""
                },
                "individual": null,
                "legalEntity": null,
                "contacts": [],
                "addresses": [],
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

        verify(
            service,
            never()
        ).create(any(ClientRequestDto.class));
    }

    @Test
    @DisplayName("Deve retornar 400 quando CPF/CNPJ já estiver cadastrado")
    void deveRetornar400QuandoCpfCnpjJaEstiverCadastrado()
        throws Exception {

        when(service.create(any(ClientRequestDto.class)))
            .thenThrow(
                new BusinessException(
                    "Já existe um cliente ativo cadastrado com este CPF/CNPJ."
                )
            );

        String json = """
            {
                "person": {
                    "tipoPessoa": "PF",
                    "name": "João da Silva",
                    "cpfCnpj": "12345678900"
                },
                "individual": {
                    "rg": "495493478"
                },
                "legalEntity": null,
                "contacts": [],
                "addresses": [],
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
                .value("Regra de Negócio"))
            .andExpect(jsonPath("$.message")
                .value(
                    "Já existe um cliente ativo cadastrado " +
                    "com este CPF/CNPJ."
                ))
            .andExpect(jsonPath("$.path")
                .value("/clients"));

        verify(service).create(any(ClientRequestDto.class));
    }

    @Test
    @DisplayName("Deve atualizar cliente e retornar 200 OK")
    void deveAtualizarCliente() throws Exception {

        ClientResponseDto response = createClientResponse();

        when(service.update(
            eq(1L),
            any(ClientRequestDto.class)
        ))
        .thenReturn(response);

        String json = """
            {
                "person": {
                    "tipoPessoa": "PF",
                    "name": "João da Silva Atualizado",
                    "cpfCnpj": "12345678900"
                },
                "individual": {
                    "rg": "495493478"
                },
                "legalEntity": null,
                "contacts": [
                    {
                        "type": "EMAIL",
                        "value": "joaoatualizado@email.com",
                        "principal": true
                    },
                    {
                        "type": "WHATSAPP",
                        "value": "32999999999",
                        "principal": false
                    }
                ],
                "addresses": [
                    {
                        "type": "RESIDENCIAL",
                        "cep": "65570-970",
                        "logradouro": "Rua Nova",
                        "numero": "200",
                        "complemento": null,
                        "bairro": "Conceição",
                        "cidade": "Araióses",
                        "uf": "MA",
                        "principal": true
                    }
                ],
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

            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON
                )
            )

            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.person.id").value(10))
            .andExpect(jsonPath("$.person.name")
                .value("João da Silva"))
            .andExpect(jsonPath("$.person.cpfCnpj")
                .value("12345678900"))
            .andExpect(jsonPath("$.active").value(true));

        verify(service).update(
            eq(1L),
            any(ClientRequestDto.class)
        );
    }

    @Test
    @DisplayName("Deve retornar 400 quando dados do cliente forem inválidos no update")
    void deveRetornar400QuandoDadosDoClienteForemInvalidosNoUpdate()
        throws Exception {

        String json = """
            {
                "person": {
                    "tipoPessoa": "",
                    "name": "",
                    "cpfCnpj": ""
                },
                "individual": null,
                "legalEntity": null,
                "contacts": [],
                "addresses": [],
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
            .andExpect(jsonPath("$.path")
                .value("/clients/1"))
            .andExpect(jsonPath("$.fieldErrors").isArray())
            .andExpect(jsonPath("$.fieldErrors").isNotEmpty());

        verify(
            service,
            never()
        ).update(
            eq(1L),
            any(ClientRequestDto.class)
        );
    }

    @Test
    @DisplayName("Deve retornar 404 ao atualizar cliente inexistente")
    void deveRetornar404AoAtualizarClienteInexistente()
        throws Exception {

        when(service.update(
            eq(999L),
            any(ClientRequestDto.class)
        ))
        .thenThrow(
            new EntityNotFoundException(
                "Cliente não encontrado com o ID: 999"
            )
        );

        String json = """
            {
                "person": {
                    "tipoPessoa": "PF",
                    "name": "João da Silva",
                    "cpfCnpj": "12345678900"
                },
                "individual": {
                    "rg": "495493478"
                },
                "legalEntity": null,
                "contacts": [],
                "addresses": [],
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
    @DisplayName("Deve retornar 400 ao atualizar com CPF/CNPJ pertencente a outra pessoa")
    void deveRetornar400AoAtualizarComCpfCnpjDeOutroCliente()
        throws Exception {

        when(service.update(
            eq(1L),
            any(ClientRequestDto.class)
        ))
        .thenThrow(
            new BusinessException(
                "CPF/CNPJ já cadastrado para outra pessoa no sistema."
            )
        );

        String json = """
            {
                "person": {
                    "tipoPessoa": "PF",
                    "name": "João da Silva",
                    "cpfCnpj": "12345678900"
                },
                "individual": {
                    "rg": "495493478"
                },
                "legalEntity": null,
                "contacts": [],
                "addresses": [],
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
                .value("Regra de Negócio"))
            .andExpect(jsonPath("$.message")
                .value(
                    "CPF/CNPJ já cadastrado para outra pessoa no sistema."
                ))
            .andExpect(jsonPath("$.path")
                .value("/clients/1"));

        verify(service).update(
            eq(1L),
            any(ClientRequestDto.class)
        );
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
    void deveRetornar404AoExcluirClienteInexistente()
        throws Exception {

        doThrow(
            new EntityNotFoundException(
                "Cliente não encontrado com o ID: 999"
            )
        )
        .when(service)
        .delete(999L);

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