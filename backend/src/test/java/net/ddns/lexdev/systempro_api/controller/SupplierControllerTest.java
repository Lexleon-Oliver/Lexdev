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
        return new SupplierResponseDto(
                1L,
                10L,
                "PJ",
                "Fornecedor Tech Ltda",
                "Tech Fornecimentos",
                "12345678000195",
                "123456789",
                "contato@techfornecimentos.com",
                "31988888888",
                "30100000",
                "Rua dos Fornecedores",
                "500",
                "Sala 101",
                "Centro",
                "Belo Horizonte",
                "MG",
                "30 DIAS",
                5,
                new BigDecimal("1000.00"),
                "Tecnologia",
                "Prazo de entrega rigoroso",
                new BankDetailsDto("001", "1234", "56789-0", "CORRENTE", "12345678000195"),
                List.of(new SupplierContactDto("Carlos", "Gerente", "carlos@tech.com", "31977777777", "Comercial")),
                List.of(),
                true
        );
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
                .andExpect(jsonPath("$.content[0].personId").value(10))
                .andExpect(jsonPath("$.content[0].name").value("Fornecedor Tech Ltda"))
                .andExpect(jsonPath("$.content[0].cpfCnpj").value("12345678000195"))
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
        ).andExpect(status().isOk());

        verify(service).findAll(
                org.mockito.ArgumentMatchers.argThat(pageable ->
                        pageable.getPageNumber() == 1 && pageable.getPageSize() == 5
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
                .andExpect(jsonPath("$.personId").value(10))
                .andExpect(jsonPath("$.tipoPessoa").value("PJ"))
                .andExpect(jsonPath("$.name").value("Fornecedor Tech Ltda"))
                .andExpect(jsonPath("$.nomeFantasia").value("Tech Fornecimentos"))
                .andExpect(jsonPath("$.cpfCnpj").value("12345678000195"))
                .andExpect(jsonPath("$.email").value("contato@techfornecimentos.com"))
                .andExpect(jsonPath("$.condicaoPagamentoPadrao").value("30 DIAS"))
                .andExpect(jsonPath("$.prazoEntregaDias").value(5))
                .andExpect(jsonPath("$.valorMinimoPedido").value(1000.00))
                .andExpect(jsonPath("$.bankDetails.banco").value("001"))
                .andExpect(jsonPath("$.contatos[0].nome").value("Carlos"))
                .andExpect(jsonPath("$.active").value(true));

        verify(service).findById(1L);
    }

    @Test
    @DisplayName("Deve retornar 404 quando fornecedor não existir")
    void deveRetornar404QuandoFornecedorNaoExistir() throws Exception {
        when(service.findById(999L))
                .thenThrow(new EntityNotFoundException("Fornecedor não encontrado com o ID: 999"));

        mockMvc.perform(get("/suppliers/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Recurso Não Encontrado"))
                .andExpect(jsonPath("$.message").value("Fornecedor não encontrado com o ID: 999"))
                .andExpect(jsonPath("$.path").value("/suppliers/999"));

        verify(service).findById(999L);
    }

    @Test
    @DisplayName("Deve criar fornecedor e retornar 201 Created")
    void deveCriarFornecedor() throws Exception {
        SupplierResponseDto response = buildSupplierResponseDto();

        when(service.create(any(SupplierRequestDto.class))).thenReturn(response);

        String json = """
                {
                    "tipoPessoa": "PJ",
                    "name": "Fornecedor Tech Ltda",
                    "nomeFantasia": "Tech Fornecimentos",
                    "cpfCnpj": "12345678000195",
                    "rgIe": "123456789",
                    "email": "contato@techfornecimentos.com",
                    "phone": "31988888888",
                    "cep": "30100000",
                    "logradouro": "Rua dos Fornecedores",
                    "numero": "500",
                    "complemento": "Sala 101",
                    "bairro": "Centro",
                    "cidade": "Belo Horizonte",
                    "uf": "MG",
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

        mockMvc.perform(
                post("/suppliers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/suppliers/1")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.personId").value(10))
                .andExpect(jsonPath("$.name").value("Fornecedor Tech Ltda"))
                .andExpect(jsonPath("$.cpfCnpj").value("12345678000195"))
                .andExpect(jsonPath("$.active").value(true));

        verify(service).create(any(SupplierRequestDto.class));
    }

    @Test
    @DisplayName("Deve retornar 400 quando os dados do fornecedor forem inválidos")
    void deveRetornar400QuandoDadosDoFornecedorForemInvalidos() throws Exception {
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
                post("/suppliers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Erro de Validação"))
                .andExpect(jsonPath("$.message").value("Um ou mais campos estão inválidos"))
                .andExpect(jsonPath("$.path").value("/suppliers"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());

        verify(service, never()).create(any(SupplierRequestDto.class));
    }

    @Test
    @DisplayName("Deve retornar 400 quando CPF/CNPJ já estiver cadastrado para fornecedor")
    void deveRetornar400QuandoCpfCnpjJaEstiverCadastrado() throws Exception {
        when(service.create(any(SupplierRequestDto.class)))
                .thenThrow(new BusinessException("Esta pessoa/empresa já está cadastrada como fornecedor ativo."));

        String json = """
                {
                    "tipoPessoa": "PJ",
                    "name": "Fornecedor Tech Ltda",
                    "nomeFantasia": "Tech Fornecimentos",
                    "cpfCnpj": "12345678000195",
                    "rgIe": "123456789",
                    "email": "contato@techfornecimentos.com",
                    "phone": "31988888888",
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
                .andExpect(jsonPath("$.error").value("Regra de Negócio"))
                .andExpect(jsonPath("$.message").value("Esta pessoa/empresa já está cadastrada como fornecedor ativo."))
                .andExpect(jsonPath("$.path").value("/suppliers"));

        verify(service).create(any(SupplierRequestDto.class));
    }

    @Test
    @DisplayName("Deve atualizar fornecedor e retornar 200 OK")
    void deveAtualizarFornecedor() throws Exception {
        SupplierResponseDto response = buildSupplierResponseDto();

        when(service.update(eq(1L), any(SupplierRequestDto.class))).thenReturn(response);

        String json = """
                {
                    "tipoPessoa": "PJ",
                    "name": "Fornecedor Tech Ltda Atualizado",
                    "nomeFantasia": "Tech Fornecimentos",
                    "cpfCnpj": "12345678000195",
                    "rgIe": "123456789",
                    "email": "contato@techfornecimentos.com",
                    "phone": "31988888888",
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
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.personId").value(10))
                .andExpect(jsonPath("$.name").value("Fornecedor Tech Ltda"))
                .andExpect(jsonPath("$.cpfCnpj").value("12345678000195"))
                .andExpect(jsonPath("$.active").value(true));

        verify(service).update(eq(1L), any(SupplierRequestDto.class));
    }

    @Test
    @DisplayName("Deve retornar 400 quando dados do fornecedor forem inválidos no update")
    void deveRetornar400QuandoDadosDoFornecedorForemInvalidosNoUpdate() throws Exception {
        String json = """
                {
                    "tipoPessoa": "",
                    "name": "",
                    "nomeFantasia": null,
                    "cpfCnpj": "",
                    "email": "email-invalido",
                    "phone": "",
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
                .andExpect(jsonPath("$.error").value("Erro de Validação"))
                .andExpect(jsonPath("$.message").value("Um ou mais campos estão inválidos"))
                .andExpect(jsonPath("$.path").value("/suppliers/1"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());

        verify(service, never()).update(eq(1L), any(SupplierRequestDto.class));
    }

    @Test
    @DisplayName("Deve retornar 404 ao atualizar fornecedor inexistente")
    void deveRetornar404AoAtualizarFornecedorInexistente() throws Exception {
        when(service.update(eq(999L), any(SupplierRequestDto.class)))
                .thenThrow(new EntityNotFoundException("Fornecedor não encontrado com o ID: 999"));

        String json = """
                {
                    "tipoPessoa": "PJ",
                    "name": "Fornecedor Tech Ltda",
                    "cpfCnpj": "12345678000195",
                    "email": "contato@techfornecimentos.com",
                    "phone": "31988888888",
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
                .andExpect(jsonPath("$.error").value("Recurso Não Encontrado"))
                .andExpect(jsonPath("$.message").value("Fornecedor não encontrado com o ID: 999"))
                .andExpect(jsonPath("$.path").value("/suppliers/999"));

        verify(service).update(eq(999L), any(SupplierRequestDto.class));
    }

    @Test
    @DisplayName("Deve retornar 400 ao atualizar com CPF/CNPJ pertencente a outra pessoa")
    void deveRetornar400AoAtualizarComCpfCnpjDeOutraPessoa() throws Exception {
        when(service.update(eq(1L), any(SupplierRequestDto.class)))
                .thenThrow(new BusinessException("CPF/CNPJ já cadastrado para outra pessoa no sistema."));

        String json = """
                {
                    "tipoPessoa": "PJ",
                    "name": "Fornecedor Tech Ltda",
                    "cpfCnpj": "12345678000195",
                    "email": "contato@techfornecimentos.com",
                    "phone": "31988888888",
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
                .andExpect(jsonPath("$.error").value("Regra de Negócio"))
                .andExpect(jsonPath("$.message").value("CPF/CNPJ já cadastrado para outra pessoa no sistema."))
                .andExpect(jsonPath("$.path").value("/suppliers/1"));

        verify(service).update(eq(1L), any(SupplierRequestDto.class));
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
    void deveRetornar404AoExcluirFornecedorInexistente() throws Exception {
        doThrow(new EntityNotFoundException("Fornecedor não encontrado com o ID: 999"))
                .when(service).delete(999L);

        mockMvc.perform(
                delete("/suppliers/999")
                        .with(csrf())
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Recurso Não Encontrado"))
                .andExpect(jsonPath("$.message").value("Fornecedor não encontrado com o ID: 999"))
                .andExpect(jsonPath("$.path").value("/suppliers/999"));

        verify(service).delete(999L);
    }
}
