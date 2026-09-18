package net.ddns.lexdev.systempro_api.controller;

import java.math.BigDecimal;
import java.util.List;

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
import net.ddns.lexdev.systempro_api.dto.BankDetailsDto;
import net.ddns.lexdev.systempro_api.dto.LegalEntityResponseDto;
import net.ddns.lexdev.systempro_api.dto.PersonAddressResponseDto;
import net.ddns.lexdev.systempro_api.dto.PersonContactResponseDto;
import net.ddns.lexdev.systempro_api.dto.PersonResponseDto;
import net.ddns.lexdev.systempro_api.dto.SupplierContactDto;
import net.ddns.lexdev.systempro_api.dto.SupplierRequestDto;
import net.ddns.lexdev.systempro_api.dto.SupplierResponseDto;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.UserRepository;
import net.ddns.lexdev.systempro_api.service.SupplierService;

@WebMvcTest(SupplierController.class)
@WithMockUser(username = "admin", roles = "ADMIN")
class SupplierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupplierService service;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    private SupplierResponseDto buildSupplierResponseDto() {

        PersonResponseDto person = new PersonResponseDto(
            10L,
            "PJ",
            "Fornecedor Tech Ltda",
            "12345678000195"
        );

        LegalEntityResponseDto legalEntity =
            new LegalEntityResponseDto(
                20L,
                "Tech Fornecimentos",
                "123456789"
            );

        List<PersonContactResponseDto> contacts = List.of(
            new PersonContactResponseDto(
                30L,
                "EMAIL",
                "contato@techfornecimentos.com",
                true,
                null
            ),
            new PersonContactResponseDto(
                31L,
                "WHATSAPP",
                "31988888888",
                false,
                null
            )
        );

        List<PersonAddressResponseDto> addresses = List.of(
            new PersonAddressResponseDto(
                40L,
                "COMERCIAL",
                "30100000",
                "Rua dos Fornecedores",
                "500",
                "Sala 101",
                "Centro",
                "Belo Horizonte",
                "MG",
                true
            )
        );

        return new SupplierResponseDto(
            1L,
            person,
            null,
            legalEntity,
            contacts,
            addresses,
            "30 DIAS",
            5,
            new BigDecimal("1000.00"),
            "Tecnologia",
            "Prazo de entrega rigoroso",
            new BankDetailsDto(
                "001",
                "1234",
                "56789-0",
                "CORRENTE",
                "12345678000195"
            ),
            List.of(
                new SupplierContactDto(
                    "Carlos",
                    "Gerente",
                    "carlos@tech.com",
                    "31977777777",
                    "Comercial"
                )
            ),
            List.of(),
            true
        );
    }

    private String validSupplierJson() {
        return """
            {
                "person": {
                    "tipoPessoa": "PJ",
                    "name": "Fornecedor Tech Ltda",
                    "cpfCnpj": "12345678000195"
                },
                "individual": null,
                "legalEntity": {
                    "nomeFantasia": "Tech Fornecimentos",
                    "inscricaoEstadual": "123456789"
                },
                "contacts": [
                    {
                        "type": "EMAIL",
                        "value": "contato@techfornecimentos.com",
                        "principal": true,
                        "description": null
                    },
                    {
                        "type": "WHATSAPP",
                        "value": "31988888888",
                        "principal": false,
                        "description": null
                    }
                ],
                "addresses": [
                    {
                        "type": "COMERCIAL",
                        "cep": "30100000",
                        "logradouro": "Rua dos Fornecedores",
                        "numero": "500",
                        "complemento": "Sala 101",
                        "bairro": "Centro",
                        "cidade": "Belo Horizonte",
                        "uf": "MG",
                        "principal": true
                    }
                ],
                "condicaoPagamentoPadrao": "30 DIAS",
                "prazoEntregaDias": 5,
                "valorMinimoPedido": 1000.00,
                "categoria": "Tecnologia",
                "observacoesComerciais": "Prazo de entrega rigoroso",
                "bankDetails": {
                    "banco": "001",
                    "agencia": "1234",
                    "conta": "56789-0",
                    "tipoConta": "CORRENTE",
                    "chavePix": "12345678000195"
                },
                "contatos": [
                    {
                        "nome": "Carlos",
                        "cargo": "Gerente",
                        "email": "carlos@tech.com",
                        "telefone": "31977777777",
                        "setor": "Comercial"
                    }
                ],
                "documentos": [],
                "active": true
            }
            """;
    }

    @Test
    @DisplayName("Deve retornar lista paginada de fornecedores")
    void deveRetornarListaPaginadaDeFornecedores() throws Exception {

        SupplierResponseDto supplier = buildSupplierResponseDto();

        PageImpl<SupplierResponseDto> page = new PageImpl<>(
            List.of(supplier),
            PageRequest.of(0, 10),
            1
        );

        when(service.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/suppliers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(1))

            .andExpect(jsonPath("$.content[0].person.id").value(10))
            .andExpect(jsonPath("$.content[0].person.tipoPessoa").value("PJ"))
            .andExpect(jsonPath("$.content[0].person.name")
                .value("Fornecedor Tech Ltda"))
            .andExpect(jsonPath("$.content[0].person.cpfCnpj")
                .value("12345678000195"))

            .andExpect(jsonPath("$.content[0].legalEntity.id").value(20))
            .andExpect(jsonPath("$.content[0].legalEntity.nomeFantasia")
                .value("Tech Fornecimentos"))
            .andExpect(jsonPath("$.content[0].legalEntity.inscricaoEstadual")
                .value("123456789"))

            .andExpect(jsonPath("$.content[0].contacts").isArray())
            .andExpect(jsonPath("$.content[0].contacts.length()").value(2))
            .andExpect(jsonPath("$.content[0].contacts[0].type")
                .value("EMAIL"))
            .andExpect(jsonPath("$.content[0].contacts[0].value")
                .value("contato@techfornecimentos.com"))

            .andExpect(jsonPath("$.content[0].addresses").isArray())
            .andExpect(jsonPath("$.content[0].addresses.length()").value(1))
            .andExpect(jsonPath("$.content[0].addresses[0].cidade")
                .value("Belo Horizonte"))

            .andExpect(jsonPath("$.content[0].condicaoPagamentoPadrao")
                .value("30 DIAS"))
            .andExpect(jsonPath("$.content[0].prazoEntregaDias").value(5))
            .andExpect(jsonPath("$.content[0].valorMinimoPedido")
                .value(1000.00))
            .andExpect(jsonPath("$.content[0].active").value(true));

        verify(service).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Deve encaminhar parâmetros de paginação ao service")
    void deveEncaminharParametrosDePaginacaoAoService() throws Exception {

        PageImpl<SupplierResponseDto> page = new PageImpl<>(
            List.of(),
            PageRequest.of(1, 5),
            0
        );

        when(service.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(
            get("/suppliers")
                .param("page", "1")
                .param("size", "5")
        )
            .andExpect(status().isOk());

        verify(service).findAll(
            org.mockito.ArgumentMatchers.argThat(pageable ->
                pageable.getPageNumber() == 1 &&
                pageable.getPageSize() == 5
            )
        );
    }

    @Test
    @DisplayName("Deve retornar fornecedor pelo ID")
    void deveRetornarFornecedorPeloId() throws Exception {

        SupplierResponseDto supplier = buildSupplierResponseDto();

        when(service.findById(1L)).thenReturn(supplier);

        mockMvc.perform(get("/suppliers/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))

            .andExpect(jsonPath("$.person.id").value(10))
            .andExpect(jsonPath("$.person.tipoPessoa").value("PJ"))
            .andExpect(jsonPath("$.person.name")
                .value("Fornecedor Tech Ltda"))
            .andExpect(jsonPath("$.person.cpfCnpj")
                .value("12345678000195"))

            .andExpect(jsonPath("$.legalEntity.id").value(20))
            .andExpect(jsonPath("$.legalEntity.nomeFantasia")
                .value("Tech Fornecimentos"))
            .andExpect(jsonPath("$.legalEntity.inscricaoEstadual")
                .value("123456789"))

            .andExpect(jsonPath("$.contacts[0].type").value("EMAIL"))
            .andExpect(jsonPath("$.contacts[0].value")
                .value("contato@techfornecimentos.com"))

            .andExpect(jsonPath("$.addresses[0].type")
                .value("COMERCIAL"))
            .andExpect(jsonPath("$.addresses[0].cidade")
                .value("Belo Horizonte"))

            .andExpect(jsonPath("$.condicaoPagamentoPadrao")
                .value("30 DIAS"))
            .andExpect(jsonPath("$.prazoEntregaDias").value(5))
            .andExpect(jsonPath("$.valorMinimoPedido")
                .value(1000.00))

            .andExpect(jsonPath("$.bankDetails.banco").value("001"))
            .andExpect(jsonPath("$.bankDetails.agencia").value("1234"))
            .andExpect(jsonPath("$.contatos[0]").exists())
            .andExpect(jsonPath("$.contatos[0].nome").value("Carlos"))

            .andExpect(jsonPath("$.active").value(true));

        verify(service).findById(1L);
    }

    @Test
    @DisplayName("Deve retornar 404 quando fornecedor não existir")
    void deveRetornar404QuandoFornecedorNaoExistir() throws Exception {

        when(service.findById(999L))
            .thenThrow(new EntityNotFoundException(
                "Fornecedor não encontrado com o ID: 999"
            ));

        mockMvc.perform(get("/suppliers/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error")
                .value("Recurso Não Encontrado"))
            .andExpect(jsonPath("$.message")
                .value("Fornecedor não encontrado com o ID: 999"))
            .andExpect(jsonPath("$.path").value("/suppliers/999"));

        verify(service).findById(999L);
    }

    @Test
    @DisplayName("Deve criar fornecedor e retornar 201 Created")
    void deveCriarFornecedor() throws Exception {

        SupplierResponseDto response = buildSupplierResponseDto();

        when(service.create(any(SupplierRequestDto.class)))
            .thenReturn(response);

        mockMvc.perform(
            post("/suppliers")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validSupplierJson())
        )
            .andExpect(status().isCreated())
            .andExpect(
                header().string(
                    "Location",
                    org.hamcrest.Matchers.endsWith("/suppliers/1")
                )
            )
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON
                )
            )
            .andExpect(jsonPath("$.id").value(1))

            .andExpect(jsonPath("$.person.id").value(10))
            .andExpect(jsonPath("$.person.tipoPessoa").value("PJ"))
            .andExpect(jsonPath("$.person.name")
                .value("Fornecedor Tech Ltda"))
            .andExpect(jsonPath("$.person.cpfCnpj")
                .value("12345678000195"))

            .andExpect(jsonPath("$.legalEntity.nomeFantasia")
                .value("Tech Fornecimentos"))

            .andExpect(jsonPath("$.active").value(true));

        verify(service).create(any(SupplierRequestDto.class));
    }

    @Test
    @DisplayName("Deve retornar 400 quando os dados do fornecedor forem inválidos")
    void deveRetornar400QuandoDadosDoFornecedorForemInvalidos()
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
                "condicaoPagamentoPadrao": null,
                "prazoEntregaDias": null,
                "valorMinimoPedido": null,
                "categoria": null,
                "observacoesComerciais": null,
                "bankDetails": null,
                "contatos": [],
                "documentos": [],
                "active": true
            }
            """;

        mockMvc.perform(
            post("/suppliers")
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
            .andExpect(jsonPath("$.path").value("/suppliers"))
            .andExpect(jsonPath("$.fieldErrors").isArray())
            .andExpect(jsonPath("$.fieldErrors").isNotEmpty());

        verify(service, never()).create(any(SupplierRequestDto.class));
    }

    @Test
    @DisplayName("Deve retornar 400 quando CPF/CNPJ já estiver cadastrado para fornecedor")
    void deveRetornar400QuandoCpfCnpjJaEstiverCadastrado()
        throws Exception {

        when(service.create(any(SupplierRequestDto.class)))
            .thenThrow(new BusinessException(
                "Esta pessoa/empresa já está cadastrada como fornecedor ativo."
            ));

        String json = """
            {
                "person": {
                    "tipoPessoa": "PJ",
                    "name": "Fornecedor Tech Ltda",
                    "cpfCnpj": "12345678000195"
                },
                "individual": null,
                "legalEntity": {
                    "nomeFantasia": "Tech Fornecimentos",
                    "inscricaoEstadual": "123456789"
                },
                "contacts": [],
                "addresses": [],
                "condicaoPagamentoPadrao": "30 DIAS",
                "prazoEntregaDias": 5,
                "valorMinimoPedido": 1000.00,
                "categoria": "Tecnologia",
                "observacoesComerciais": "Prazo de entrega rigoroso",
                "bankDetails": null,
                "contatos": [],
                "documentos": [],
                "active": true
            }
            """;

        mockMvc.perform(
            post("/suppliers")
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
                    "Esta pessoa/empresa já está cadastrada como fornecedor ativo."
                ))
            .andExpect(jsonPath("$.path").value("/suppliers"));

        verify(service).create(any(SupplierRequestDto.class));
    }

    @Test
    @DisplayName("Deve atualizar fornecedor e retornar 200 OK")
    void deveAtualizarFornecedor() throws Exception {

        SupplierResponseDto response = buildSupplierResponseDto();

        when(service.update(eq(1L), any(SupplierRequestDto.class)))
            .thenReturn(response);

        String json = """
            {
                "person": {
                    "tipoPessoa": "PJ",
                    "name": "Fornecedor Tech Ltda Atualizado",
                    "cpfCnpj": "12345678000195"
                },
                "individual": null,
                "legalEntity": {
                    "nomeFantasia": "Tech Fornecimentos",
                    "inscricaoEstadual": "123456789"
                },
                "contacts": [
                    {
                        "type": "EMAIL",
                        "value": "contato@techfornecimentos.com",
                        "principal": true
                    }
                ],
                "addresses": [],
                "condicaoPagamentoPadrao": "30 DIAS",
                "prazoEntregaDias": 5,
                "valorMinimoPedido": 1000.00,
                "categoria": "Tecnologia",
                "observacoesComerciais": "Prazo de entrega rigoroso",
                "bankDetails": null,
                "contatos": [],
                "documentos": [],
                "active": true
            }
            """;

        mockMvc.perform(
            put("/suppliers/1")
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
                .value("Fornecedor Tech Ltda"))
            .andExpect(jsonPath("$.person.cpfCnpj")
                .value("12345678000195"))
            .andExpect(jsonPath("$.active").value(true));

        verify(service).update(
            eq(1L),
            any(SupplierRequestDto.class)
        );
    }

    @Test
    @DisplayName("Deve retornar 400 quando dados do fornecedor forem inválidos no update")
    void deveRetornar400QuandoDadosDoFornecedorForemInvalidosNoUpdate()
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
                "condicaoPagamentoPadrao": null,
                "prazoEntregaDias": null,
                "valorMinimoPedido": null,
                "categoria": null,
                "observacoesComerciais": null,
                "bankDetails": null,
                "contatos": [],
                "documentos": [],
                "active": true
            }
            """;

        mockMvc.perform(
            put("/suppliers/1")
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
            .andExpect(jsonPath("$.path").value("/suppliers/1"))
            .andExpect(jsonPath("$.fieldErrors").isArray())
            .andExpect(jsonPath("$.fieldErrors").isNotEmpty());

        verify(
            service,
            never()
        ).update(
            eq(1L),
            any(SupplierRequestDto.class)
        );
    }

    @Test
    @DisplayName("Deve retornar 404 ao atualizar fornecedor inexistente")
    void deveRetornar404AoAtualizarFornecedorInexistente()
        throws Exception {

        when(service.update(eq(999L), any(SupplierRequestDto.class)))
            .thenThrow(new EntityNotFoundException(
                "Fornecedor não encontrado com o ID: 999"
            ));

        String json = """
            {
                "person": {
                    "tipoPessoa": "PJ",
                    "name": "Fornecedor Tech Ltda",
                    "cpfCnpj": "12345678000195"
                },
                "individual": null,
                "legalEntity": {
                    "nomeFantasia": "Tech Fornecimentos",
                    "inscricaoEstadual": "123456789"
                },
                "contacts": [],
                "addresses": [],
                "condicaoPagamentoPadrao": "30 DIAS",
                "prazoEntregaDias": 5,
                "valorMinimoPedido": 1000.00,
                "categoria": "Tecnologia",
                "observacoesComerciais": null,
                "bankDetails": null,
                "contatos": [],
                "documentos": [],
                "active": true
            }
            """;

        mockMvc.perform(
            put("/suppliers/999")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error")
                .value("Recurso Não Encontrado"))
            .andExpect(jsonPath("$.message")
                .value("Fornecedor não encontrado com o ID: 999"))
            .andExpect(jsonPath("$.path").value("/suppliers/999"));

        verify(service).update(
            eq(999L),
            any(SupplierRequestDto.class)
        );
    }

    @Test
    @DisplayName("Deve retornar 400 ao atualizar com CPF/CNPJ pertencente a outra pessoa")
    void deveRetornar400AoAtualizarComCpfCnpjDeOutraPessoa()
        throws Exception {

        when(service.update(eq(1L), any(SupplierRequestDto.class)))
            .thenThrow(new BusinessException(
                "CPF/CNPJ já cadastrado para outra pessoa no sistema."
            ));

        String json = """
            {
                "person": {
                    "tipoPessoa": "PJ",
                    "name": "Fornecedor Tech Ltda",
                    "cpfCnpj": "12345678000195"
                },
                "individual": null,
                "legalEntity": {
                    "nomeFantasia": "Tech Fornecimentos",
                    "inscricaoEstadual": "123456789"
                },
                "contacts": [],
                "addresses": [],
                "condicaoPagamentoPadrao": "30 DIAS",
                "prazoEntregaDias": 5,
                "valorMinimoPedido": 1000.00,
                "categoria": "Tecnologia",
                "observacoesComerciais": null,
                "bankDetails": null,
                "contatos": [],
                "documentos": [],
                "active": true
            }
            """;

        mockMvc.perform(
            put("/suppliers/1")
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
            .andExpect(jsonPath("$.path").value("/suppliers/1"));

        verify(service).update(
            eq(1L),
            any(SupplierRequestDto.class)
        );
    }

    @Test
    @DisplayName("Deve excluir fornecedor e retornar 204 No Content")
    void deveExcluirFornecedor() throws Exception {

        mockMvc.perform(
            delete("/suppliers/1")
                .with(csrf())
        )
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(service).delete(1L);
    }

    @Test
    @DisplayName("Deve retornar 404 ao excluir fornecedor inexistente")
    void deveRetornar404AoExcluirFornecedorInexistente()
        throws Exception {

        doThrow(new EntityNotFoundException(
            "Fornecedor não encontrado com o ID: 999"
        ))
            .when(service)
            .delete(999L);

        mockMvc.perform(
            delete("/suppliers/999")
                .with(csrf())
        )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error")
                .value("Recurso Não Encontrado"))
            .andExpect(jsonPath("$.message")
                .value("Fornecedor não encontrado com o ID: 999"))
            .andExpect(jsonPath("$.path").value("/suppliers/999"));

        verify(service).delete(999L);
    }
}