package net.ddns.lexdev.systempro_api.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.Supplier;
import net.ddns.lexdev.systempro_api.domain.SupplierContact;
import net.ddns.lexdev.systempro_api.domain.SupplierDocument;
import net.ddns.lexdev.systempro_api.dto.BankDetailsDto;
import net.ddns.lexdev.systempro_api.dto.IndividualPersonRequestDto;
import net.ddns.lexdev.systempro_api.dto.PersonAddressRequestDto;
import net.ddns.lexdev.systempro_api.dto.PersonRequestDto;
import net.ddns.lexdev.systempro_api.dto.SupplierContactDto;
import net.ddns.lexdev.systempro_api.dto.SupplierDocumentDto;
import net.ddns.lexdev.systempro_api.dto.SupplierRequestDto;
import net.ddns.lexdev.systempro_api.dto.SupplierResponseDto;
import net.ddns.lexdev.systempro_api.enums.TipoContaBancaria;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.integration.IntegrationTestBase;
import net.ddns.lexdev.systempro_api.repository.SupplierRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SupplierServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deveCadastrarFornecedorCompletoEPersistirRelacionamentos() {

        // ============================================================
        // Dados do fornecedor
        // ============================================================

        SupplierRequestDto dto = new SupplierRequestDto(

            // Person
            new PersonRequestDto(
                "162.488.173-40",
                "PF",
                "Fornecedor Integração Teste"
            ),

            // Individual
            new IndividualPersonRequestDto(
                "11111111"
            ),

            // LegalEntity
            null,

            // Contatos comuns da Person
            List.of(),

            // Endereços comuns da Person
            List.of( 
                new PersonAddressRequestDto( 
                    "COMERCIAL", 
                    "36200-000", 
                    "Rua das Empresas", 
                    "100", 
                    "Sala 201", 
                    "Centro", 
                    "Barbacena", 
                    "MG", 
                    true 
                ) 
            ),

            // Dados comerciais
            "30 DIAS",
            7,
            new BigDecimal("1500.00"),
            "Material de Escritório",
            "Fornecedor criado pelo teste de integração",

            // Dados bancários
            new BankDetailsDto(
                "001",
                "1234-5",
                "67890-1",
                "CORRENTE",
                "fornecedor@pix.com"
            ),

            // Contatos específicos do fornecedor
            List.of(
                new SupplierContactDto(
                    "João da Silva",
                    "Vendedor",
                    "joao@fornecedor.com",
                    "(32) 99999-1111",
                    "Comercial"
                ),
                new SupplierContactDto(
                    "Maria Souza",
                    "Financeiro",
                    "maria@fornecedor.com",
                    "(32) 99999-2222",
                    "Financeiro"
                )
            ),

            // Documentos específicos do fornecedor
            List.of(
                new SupplierDocumentDto(
                    "CONTRATO",
                    "CONTRATO-2026-001",
                    LocalDate.of(2027, 9, 21)
                ),
                new SupplierDocumentDto(
                    "CERTIDAO",
                    "https://fornecedor.com/certidao",
                    LocalDate.of(2027, 12, 31)
                )
            ),

            true
        );

        // ============================================================
        // Criação
        // ============================================================

        SupplierResponseDto response = supplierService.create(dto);

        // ============================================================
        // Validação do retorno
        // ============================================================

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        // ============================================================
        // Garante sincronização com PostgreSQL
        // ============================================================

        entityManager.flush();
        entityManager.clear();

        // ============================================================
        // Recarrega do banco
        // ============================================================

        Supplier supplier = supplierRepository
            .findByIdWithPerson(response.id())
            .orElseThrow();

        // ============================================================
        // Supplier
        // ============================================================

        assertThat(supplier.getId())
            .isEqualTo(response.id());

        assertThat(supplier.isActive())
            .isTrue();

        // ============================================================
        // Person
        // ============================================================

        assertThat(supplier.getPerson())
            .isNotNull();

        assertThat(supplier.getPerson().getCpfCnpj())
            .isEqualTo("16248817340");

        assertThat(supplier.getPerson().getName())
            .isEqualTo("Fornecedor Integração Teste");

        assertThat(supplier.getPerson().getTipoPessoa())
            .isEqualTo(TipoPessoa.PF);

        // ============================================================
        // Dados comerciais
        // ============================================================

        assertThat(supplier.getCondicaoPagamentoPadrao())
            .isEqualTo("30 DIAS");

        assertThat(supplier.getPrazoEntregaDias())
            .isEqualTo(7);

        assertThat(supplier.getValorMinimoPedido())
            .isEqualByComparingTo("1500.00");

        assertThat(supplier.getCategoria())
            .isEqualTo("Material de Escritório");

        assertThat(supplier.getObservacoesComerciais())
            .isEqualTo("Fornecedor criado pelo teste de integração");

        // ============================================================
        // Dados bancários
        // ============================================================

        assertThat(supplier.getBankDetails())
            .isNotNull();

        assertThat(supplier.getBankDetails().getBanco())
            .isEqualTo("001");

        assertThat(supplier.getBankDetails().getAgencia())
            .isEqualTo("1234-5");

        assertThat(supplier.getBankDetails().getConta())
            .isEqualTo("67890-1");

        assertThat(supplier.getBankDetails().getTipoConta())
            .isEqualTo(TipoContaBancaria.CORRENTE);

        assertThat(supplier.getBankDetails().getChavePix())
            .isEqualTo("fornecedor@pix.com");

        // ============================================================
        // Contatos específicos do fornecedor
        // ============================================================

        assertThat(supplier.getContatos())
            .hasSize(2);

        assertThat(supplier.getContatos())
            .extracting(SupplierContact::getNome)
            .containsExactlyInAnyOrder(
                "João da Silva",
                "Maria Souza"
            );

        // ============================================================
        // Documentos
        // ============================================================

        assertThat(supplier.getDocumentos())
            .hasSize(2);

        assertThat(supplier.getDocumentos())
            .extracting(SupplierDocument::getTipoDocumento)
            .containsExactlyInAnyOrder(
                "CONTRATO",
                "CERTIDAO"
            );
    }

    @Test
    void naoDevePermitirCadastrarMesmoCpfCnpjComoFornecedorAtivo() {

        SupplierRequestDto dto = new SupplierRequestDto(

            new PersonRequestDto(
                "162.488.173-40",
                "PF",
                "Fornecedor Original"
            ),

            new IndividualPersonRequestDto(
                "11111111"
            ),

            null,
            List.of(),
            List.of(),

            "30 DIAS",
            7,
            new BigDecimal("1000.00"),
            "Material",
            "Primeiro cadastro",

            null,
            List.of(),
            List.of(),

            true
        );

        // Primeiro cadastro
        supplierService.create(dto);

        // Segundo cadastro com o mesmo CPF
        assertThatThrownBy(() ->
            supplierService.create(
                new SupplierRequestDto(
                    new PersonRequestDto(
                        "162.488.173-40",
                        "PF",
                        "Fornecedor Duplicado"
                    ),
                    new IndividualPersonRequestDto(
                        "22222222"
                    ),
                    null,
                    List.of(),
                    List.of(),
                    "60 DIAS",
                    15,
                    new BigDecimal("2000.00"),
                    "Outro",
                    "Tentativa duplicada",
                    null,
                    List.of(),
                    List.of(),
                    true
                )
            )
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "Esta pessoa/empresa já está cadastrada como fornecedor ativo."
            );
    }

    @Test
    void deveAtualizarFornecedorCompletoEPersistirAlteracoes() {

        // ============================================================
        // 1. Cadastra o fornecedor inicial
        // ============================================================

        SupplierRequestDto cadastroDto = new SupplierRequestDto(

            // Person
            new PersonRequestDto(
                "162.488.173-40",
                "PF",
                "Fornecedor Original"
            ),

            // Individual
            new IndividualPersonRequestDto(
                "11111111"
            ),

            // Legal Entity
            null,

            // Contatos Person
            List.of(),

            // Endereços Person
            List.of(
                new PersonAddressRequestDto(
                    "COMERCIAL",
                    "36200-000",
                    "Rua Original",
                    "100",
                    "Sala 1",
                    "Centro",
                    "Barbacena",
                    "MG",
                    true
                )
            ),

            // Dados comerciais
            "30 DIAS",
            7,
            new BigDecimal("1000.00"),
            "Material de Escritório",
            "Cadastro original",

            // Banco
            new BankDetailsDto(
                "001",
                "1234-5",
                "11111-1",
                "CORRENTE",
                "original@pix.com"
            ),

            // Contatos específicos
            List.of(
                new SupplierContactDto(
                    "João Original",
                    "Vendedor",
                    "joao.original@fornecedor.com",
                    "(32) 99999-1111",
                    "Comercial"
                ),
                new SupplierContactDto(
                    "Maria Original",
                    "Financeiro",
                    "maria.original@fornecedor.com",
                    "(32) 99999-2222",
                    "Financeiro"
                )
            ),

            // Documentos
            List.of(
                new SupplierDocumentDto(
                    "CONTRATO",
                    "CONTRATO-ORIGINAL",
                    LocalDate.of(2027, 9, 21)
                ),
                new SupplierDocumentDto(
                    "CERTIDAO",
                    "CERTIDAO-ORIGINAL",
                    LocalDate.of(2027, 12, 31)
                )
            ),

            true
        );

        SupplierResponseDto criado =
            supplierService.create(cadastroDto);

        assertThat(criado).isNotNull();
        assertThat(criado.id()).isNotNull();

        Long supplierId = criado.id();

        // ============================================================
        // 2. Atualiza o fornecedor
        // ============================================================

        SupplierRequestDto atualizacaoDto = new SupplierRequestDto(

            // Person alterada
            new PersonRequestDto(
                "162.488.173-40",
                "PF",
                "Fornecedor Atualizado"
            ),

            // Individual alterada
            new IndividualPersonRequestDto(
                "99999999"
            ),

            // Legal Entity
            null,

            // Contatos Person
            List.of(),

            // Endereço alterado
            List.of(
                new PersonAddressRequestDto(
                    "COMERCIAL",
                    "36200-111",
                    "Rua Atualizada",
                    "200",
                    "Sala 2",
                    "São José",
                    "Barbacena",
                    "MG",
                    true
                )
            ),

            // Dados comerciais alterados
            "60 DIAS",
            15,
            new BigDecimal("2500.00"),
            "Equipamentos",
            "Dados comerciais atualizados",

            // Banco substituído
            new BankDetailsDto(
                "341",
                "5678-9",
                "99999-9",
                "POUPANCA",
                "atualizado@pix.com"
            ),

            // Lista de contatos substituída
            List.of(
                new SupplierContactDto(
                    "Carlos Atualizado",
                    "Compras",
                    "carlos@fornecedor.com",
                    "(32) 98888-3333",
                    "Compras"
                )
            ),

            // Lista de documentos substituída
            List.of(
                new SupplierDocumentDto(
                    "CONTRATO",
                    "CONTRATO-ATUALIZADO",
                    LocalDate.of(2028, 9, 21)
                )
            ),

            true
        );

        SupplierResponseDto atualizado =
            supplierService.update(supplierId, atualizacaoDto);

        // ============================================================
        // 3. Valida retorno
        // ============================================================

        assertThat(atualizado).isNotNull();
        assertThat(atualizado.id()).isEqualTo(supplierId);

        // ============================================================
        // 4. Garante persistência real
        // ============================================================

        entityManager.flush();
        entityManager.clear();

        // ============================================================
        // 5. Recarrega do banco
        // ============================================================

        Supplier supplier = supplierRepository
            .findByIdWithPerson(supplierId)
            .orElseThrow();

        // ============================================================
        // 6. Supplier
        // ============================================================

        assertThat(supplier.getId())
            .isEqualTo(supplierId);

        assertThat(supplier.isActive())
            .isTrue();

        // ============================================================
        // 7. Person atualizada
        // ============================================================

        assertThat(supplier.getPerson())
            .isNotNull();

        assertThat(supplier.getPerson().getCpfCnpj())
            .isEqualTo("16248817340");

        assertThat(supplier.getPerson().getName())
            .isEqualTo("Fornecedor Atualizado");

        assertThat(supplier.getPerson().getTipoPessoa())
            .isEqualTo(TipoPessoa.PF);

        // ============================================================
        // 8. Dados comerciais atualizados
        // ============================================================

        assertThat(supplier.getCondicaoPagamentoPadrao())
            .isEqualTo("60 DIAS");

        assertThat(supplier.getPrazoEntregaDias())
            .isEqualTo(15);

        assertThat(supplier.getValorMinimoPedido())
            .isEqualByComparingTo("2500.00");

        assertThat(supplier.getCategoria())
            .isEqualTo("Equipamentos");

        assertThat(supplier.getObservacoesComerciais())
            .isEqualTo("Dados comerciais atualizados");

        // ============================================================
        // 9. Dados bancários substituídos
        // ============================================================

        assertThat(supplier.getBankDetails())
            .isNotNull();

        assertThat(supplier.getBankDetails().getBanco())
            .isEqualTo("341");

        assertThat(supplier.getBankDetails().getAgencia())
            .isEqualTo("5678-9");

        assertThat(supplier.getBankDetails().getConta())
            .isEqualTo("99999-9");

        assertThat(supplier.getBankDetails().getTipoConta())
            .isEqualTo(TipoContaBancaria.POUPANCA);

        assertThat(supplier.getBankDetails().getChavePix())
            .isEqualTo("atualizado@pix.com");

        // ============================================================
        // 10. Contatos substituídos
        // ============================================================

        assertThat(supplier.getContatos())
            .hasSize(1);

        assertThat(supplier.getContatos())
            .extracting(SupplierContact::getNome)
            .containsExactly("Carlos Atualizado");

        assertThat(supplier.getContatos())
            .extracting(SupplierContact::getEmail)
            .containsExactly("carlos@fornecedor.com");

        // ============================================================
        // 11. Documentos substituídos
        // ============================================================

        assertThat(supplier.getDocumentos())
            .hasSize(1);

        assertThat(supplier.getDocumentos())
            .extracting(SupplierDocument::getTipoDocumento)
            .containsExactly("CONTRATO");

        assertThat(supplier.getDocumentos())
            .extracting(SupplierDocument::getNumeroOuUrl)
            .containsExactly("CONTRATO-ATUALIZADO");
    }



    @Test
    void deveDesativarFornecedorSemExcluirRegistroDoBanco() {

        // ============================================================
        // 1. Cadastra fornecedor
        // ============================================================

        SupplierRequestDto dto = new SupplierRequestDto(

            new PersonRequestDto(
                "162.488.173-40",
                "PF",
                "Fornecedor Para Exclusão"
            ),

            new IndividualPersonRequestDto(
                "11111111"
            ),

            null,

            List.of(),

            List.of(),

            "30 DIAS",
            7,
            new BigDecimal("1000.00"),
            "Material",
            "Fornecedor criado para teste de exclusão",

            null,

            List.of(
                new SupplierContactDto(
                    "João Teste",
                    "Vendedor",
                    "joao@teste.com",
                    "(32) 99999-1111",
                    "Comercial"
                )
            ),

            List.of(
                new SupplierDocumentDto(
                    "CONTRATO",
                    "CONTRATO-DELETE-001",
                    LocalDate.of(2027, 12, 31)
                )
            ),

            true
        );

        SupplierResponseDto criado =
            supplierService.create(dto);

        assertThat(criado).isNotNull();
        assertThat(criado.id()).isNotNull();

        Long supplierId = criado.id();

        // ============================================================
        // 2. Executa exclusão lógica
        // ============================================================

        supplierService.delete(supplierId);

        // ============================================================
        // 3. Garante persistência real
        // ============================================================

        entityManager.flush();
        entityManager.clear();

        // ============================================================
        // 4. Consulta diretamente a tabela
        //    sem passar pelo @SQLRestriction
        // ============================================================

        Object[] row = (Object[]) entityManager
            .createNativeQuery("""
                SELECT id, active
                FROM tb_supplier
                WHERE id = :id
                """)
            .setParameter("id", supplierId)
            .getSingleResult();

        // ============================================================
        // 5. O registro ainda existe
        // ============================================================

        assertThat(((Number) row[0]).longValue())
            .isEqualTo(supplierId);

        // ============================================================
        // 6. Mas está inativo
        // ============================================================

        assertThat((Boolean) row[1])
            .isFalse();
    }


    @Test
    void deveRetornarSomenteFornecedoresAtivosNaConsultaPaginada() {

        // ============================================================
        // 1. Cadastra fornecedor ativo
        // ============================================================

        SupplierRequestDto fornecedorAtivoDto = new SupplierRequestDto(

            new PersonRequestDto(
                "346.070.330-06",
                "PF",
                "Fornecedor Ativo"
            ),

            new IndividualPersonRequestDto(
                "11111111"
            ),

            null,

            List.of(),

            List.of(),

            "30 DIAS",
            5,
            new BigDecimal("1000.00"),
            "Material",
            "Fornecedor ativo",

            null,

            List.of(),

            List.of(),

            true
        );

        SupplierResponseDto fornecedorAtivo =
            supplierService.create(fornecedorAtivoDto);

        // ============================================================
        // 2. Cadastra segundo fornecedor
        // ============================================================

        SupplierRequestDto fornecedorInativoDto = new SupplierRequestDto(

            new PersonRequestDto(
                "456.113.470-02",
                "PF",
                "Fornecedor Que Sera Inativado"
            ),

            new IndividualPersonRequestDto(
                "22222222"
            ),

            null,

            List.of(),

            List.of(),

            "45 DIAS",
            10,
            new BigDecimal("2000.00"),
            "Equipamentos",
            "Fornecedor que será desativado",

            null,

            List.of(),

            List.of(),

            true
        );

        SupplierResponseDto fornecedorInativo =
            supplierService.create(fornecedorInativoDto);

        // ============================================================
        // 3. Garante que os dois foram cadastrados
        // ============================================================

        assertThat(fornecedorAtivo.id()).isNotNull();
        assertThat(fornecedorInativo.id()).isNotNull();

        assertThat(fornecedorAtivo.id())
            .isNotEqualTo(fornecedorInativo.id());

        // ============================================================
        // 4. Desativa o segundo fornecedor
        // ============================================================

        supplierService.delete(fornecedorInativo.id());

        // ============================================================
        // 5. Garante persistência no banco
        // ============================================================

        entityManager.flush();
        entityManager.clear();

        // ============================================================
        // 6. Consulta somente fornecedores ativos
        // ============================================================

        Page<SupplierResponseDto> resultado =
            supplierService.findAll(
                PageRequest.of(0, 10)
            );

        // ============================================================
        // 7. Deve retornar somente o fornecedor ativo
        // ============================================================

        assertThat(resultado.getContent())
            .hasSize(1);

        assertThat(resultado.getContent())
            .extracting(SupplierResponseDto::id)
            .containsExactly(fornecedorAtivo.id());

        // ============================================================
        // 8. O fornecedor inativo não pode aparecer
        // ============================================================

        assertThat(resultado.getContent())
            .extracting(SupplierResponseDto::id)
            .doesNotContain(fornecedorInativo.id());

        // ============================================================
        // 9. Metadados da paginação
        // ============================================================

        assertThat(resultado.getTotalElements())
            .isEqualTo(1);

        assertThat(resultado.getTotalPages())
            .isEqualTo(1);
    }

    @Test
    void deveNaoLocalizarFornecedorInativoPorId() {

        // ============================================================
        // 1. Cadastra fornecedor
        // ============================================================

        SupplierRequestDto dto = new SupplierRequestDto(

            new PersonRequestDto(
                "162.488.173-40",
                "PF",
                "Fornecedor Para Inativar"
            ),

            new IndividualPersonRequestDto(
                "11111111"
            ),

            null,

            List.of(),

            List.of(),

            "30 DIAS",
            7,
            new BigDecimal("1000.00"),
            "Material",
            "Fornecedor de teste",

            null,

            List.of(),

            List.of(),

            true
        );

        SupplierResponseDto criado =
            supplierService.create(dto);

        assertThat(criado).isNotNull();
        assertThat(criado.id()).isNotNull();

        Long supplierId = criado.id();

        // ============================================================
        // 2. Desativa o fornecedor
        // ============================================================

        supplierService.delete(supplierId);

        // ============================================================
        // 3. Garante persistência
        // ============================================================

        entityManager.flush();
        entityManager.clear();

        // ============================================================
        // 4. findById() deve considerar o fornecedor inexistente
        //    para a camada de negócio
        // ============================================================

        assertThatThrownBy(() ->
            supplierService.findById(supplierId)
        )
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage(
                "Fornecedor não encontrado com o ID: " + supplierId
            );
    }


    @Test
    void naoDevePermitirCadastrarFornecedorQuandoPersonEstiverInativa() {

        // ============================================================
        // 1. Cria o fornecedor original
        // ============================================================

        SupplierRequestDto primeiroDto = new SupplierRequestDto(

            new PersonRequestDto(
                "162.488.173-40",
                "PF",
                "Fornecedor Original"
            ),

            new IndividualPersonRequestDto(
                "11111111"
            ),

            null,

            List.of(),

            List.of(),

            "30 DIAS",
            7,
            new BigDecimal("1000.00"),
            "Material",
            "Fornecedor original",

            null,

            List.of(),

            List.of(),

            true
        );

        SupplierResponseDto criado =
            supplierService.create(primeiroDto);

        assertThat(criado).isNotNull();
        assertThat(criado.id()).isNotNull();

        Long supplierId = criado.id();

        // ============================================================
        // 2. Obtém o Supplier e sua Person
        // ============================================================

        Supplier supplier =
            supplierRepository.findByIdWithPerson(supplierId)
                .orElseThrow();

        Person person = supplier.getPerson();

        assertThat(person).isNotNull();
        assertThat(person.isActive()).isTrue();

        // ============================================================
        // 3. Inativa o Supplier
        // ============================================================

        supplierService.delete(supplierId);

        // ============================================================
        // 4. Inativa também a Person
        //
        // Neste teste estamos simulando o cenário em que a Person
        // foi previamente inativada pelo fluxo administrativo/suporte.
        // ============================================================

        person.setActive(false);

        entityManager.flush();
        entityManager.clear();

        // ============================================================
        // 5. Tenta cadastrar outro fornecedor com o mesmo CPF
        // ============================================================

        SupplierRequestDto segundoDto = new SupplierRequestDto(

            new PersonRequestDto(
                "162.488.173-40",
                "PF",
                "Tentativa de Novo Fornecedor"
            ),

            new IndividualPersonRequestDto(
                "22222222"
            ),

            null,

            List.of(),

            List.of(),

            "60 DIAS",
            15,
            new BigDecimal("2000.00"),
            "Equipamentos",
            "Tentativa de reutilizar CPF/CNPJ",

            null,

            List.of(),

            List.of(),

            true
        );

        // ============================================================
        // 6. Deve bloquear o cadastro
        // ============================================================

        assertThatThrownBy(() ->
            supplierService.create(segundoDto)
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "Já existe um cadastro inativado para este CPF/CNPJ. " +
                "Solicite a reativação ao Suporte."
            );
    }


    @Test
    void deveRejeitarTipoDeContaBancariaInvalido() {

        // ============================================================
        // Cadastro com tipo de conta inválido
        // ============================================================

        SupplierRequestDto dto = new SupplierRequestDto(

            new PersonRequestDto(
                "162.488.173-40",
                "PF",
                "Fornecedor Tipo Conta Inválido"
            ),

            new IndividualPersonRequestDto(
                "11111111"
            ),

            null,

            List.of(),

            List.of(),

            "30 DIAS",
            7,
            new BigDecimal("1000.00"),
            "Material",
            "Teste de tipo de conta inválido",

            new BankDetailsDto(
                "001",
                "1234-5",
                "67890-1",
                "TIPO_INEXISTENTE",
                "teste@pix.com"
            ),

            List.of(),

            List.of(),

            true
        );

        // ============================================================
        // Deve rejeitar
        // ============================================================

        assertThatThrownBy(() ->
            supplierService.create(dto)
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "Tipo de conta bancária inválido: TIPO_INEXISTENTE"
            );
    }


    @Test
    void devePermitirCadastrarFornecedorSemDadosBancarios() {

        // ============================================================
        // Cadastro sem BankDetails, contatos e documentos
        // ============================================================

        SupplierRequestDto dto = new SupplierRequestDto(

            new PersonRequestDto(
                "456.113.470-02",
                "PF",
                "Fornecedor Sem Dados Bancarios"
            ),

            new IndividualPersonRequestDto(
                "22222222"
            ),

            null,

            List.of(),

            List.of(),

            "30 DIAS",
            5,
            new BigDecimal("500.00"),
            "Material",
            "Fornecedor sem informações bancárias",

            null,

            List.of(),

            List.of(),

            true
        );

        // ============================================================
        // Cria
        // ============================================================

        SupplierResponseDto response =
            supplierService.create(dto);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        // ============================================================
        // Garante persistência
        // ============================================================

        entityManager.flush();
        entityManager.clear();

        // ============================================================
        // Recarrega
        // ============================================================

        Supplier supplier =
            supplierRepository.findByIdWithPerson(response.id())
                .orElseThrow();

        // ============================================================
        // Dados opcionais podem estar ausentes
        // ============================================================

        assertThat(supplier.getBankDetails())
            .isNull();

        assertThat(supplier.getContatos())
            .isEmpty();

        assertThat(supplier.getDocumentos())
            .isEmpty();
    }


    @Test
    void devePermitirCadastrarFornecedorComListasVaziasDeContatosEDocumentos() {

        // ============================================================
        // Cadastro com listas vazias explicitamente informadas
        // ============================================================

        SupplierRequestDto dto = new SupplierRequestDto(

            new PersonRequestDto(
                "456.113.470-02",
                "PF",
                "Fornecedor Listas Vazias"
            ),

            new IndividualPersonRequestDto(
                "33333333"
            ),

            null,

            List.of(),

            List.of(),

            "45 DIAS",
            10,
            new BigDecimal("750.00"),
            "Equipamentos",
            "Teste com listas vazias",

            null,

            List.of(),

            List.of(),

            true
        );

        SupplierResponseDto response =
            supplierService.create(dto);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        entityManager.flush();
        entityManager.clear();

        Supplier supplier =
            supplierRepository.findByIdWithPerson(response.id())
                .orElseThrow();

        assertThat(supplier.getContatos())
            .isEmpty();

        assertThat(supplier.getDocumentos())
            .isEmpty();
    }


    @Test
    void devePermitirCriarFornecedorInativo() {

        // ============================================================
        // Cadastro com active = false
        // ============================================================

        SupplierRequestDto dto = new SupplierRequestDto(

            new PersonRequestDto(
                "456.113.470-02",
                "PF",
                "Fornecedor Criado Inativo"
            ),

            new IndividualPersonRequestDto(
                "44444444"
            ),

            null,

            List.of(),

            List.of(),

            "30 DIAS",
            5,
            new BigDecimal("900.00"),
            "Material",
            "Fornecedor criado como inativo",

            null,

            List.of(),

            List.of(),

            false
        );

        SupplierResponseDto response =
            supplierService.create(dto);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        Long supplierId = response.id();

        // ============================================================
        // Persistência real
        // ============================================================

        entityManager.flush();
        entityManager.clear();

        // ============================================================
        // Como o Supplier possui @SQLRestriction(active = true),
        // a consulta normal não deve localizar esse fornecedor.
        // ============================================================

        assertThatThrownBy(() ->
            supplierService.findById(supplierId)
        )
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage(
                "Fornecedor não encontrado com o ID: " + supplierId
            );

        // ============================================================
        // Confirma diretamente no banco que o registro existe
        // e está realmente inativo.
        // ============================================================

        Object[] row = (Object[]) entityManager
            .createNativeQuery("""
                SELECT id, active
                FROM tb_supplier
                WHERE id = :id
                """)
            .setParameter("id", supplierId)
            .getSingleResult();

        assertThat(((Number) row[0]).longValue())
            .isEqualTo(supplierId);

        assertThat((Boolean) row[1])
            .isFalse();
    }


    @Test
    void deveLancarExcecaoAoAtualizarFornecedorInexistente() {

        Long supplierId = 999999999L;

        SupplierRequestDto dto = new SupplierRequestDto(

            new PersonRequestDto(
                "162.488.173-40",
                "PF",
                "Fornecedor Inexistente"
            ),

            new IndividualPersonRequestDto(
                "55555555"
            ),

            null,

            List.of(),

            List.of(),

            "30 DIAS",
            5,
            new BigDecimal("1000.00"),
            "Material",
            "Teste de fornecedor inexistente",

            null,

            List.of(),

            List.of(),

            true
        );

        assertThatThrownBy(() ->
            supplierService.update(supplierId, dto)
        )
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage(
                "Fornecedor não encontrado com o ID: " + supplierId
            );
    }


    @Test
    void deveLancarExcecaoAoExcluirFornecedorInexistente() {

        Long supplierId = 999999999L;

        assertThatThrownBy(() ->
            supplierService.delete(supplierId)
        )
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage(
                "Fornecedor não encontrado com o ID: " + supplierId
            );
    }


    @Test
    void naoDevePermitirAtualizarFornecedorComCpfCnpjDeOutraPerson() {

        // ============================================================
        // 1. Cria o primeiro fornecedor
        // ============================================================

        SupplierResponseDto primeiro = supplierService.create(

            new SupplierRequestDto(

                new PersonRequestDto(
                    "115.725.240-06",
                    "PF",
                    "Fornecedor Um"
                ),

                new IndividualPersonRequestDto(
                    "11111111"
                ),

                null,

                List.of(),
                List.of(),

                "30 DIAS",
                5,
                new BigDecimal("1000.00"),
                "Material",
                "Primeiro fornecedor",

                null,

                List.of(),
                List.of(),

                true
            )
        );

        // ============================================================
        // 2. Cria o segundo fornecedor
        // ============================================================

        SupplierResponseDto segundo = supplierService.create(

            new SupplierRequestDto(

                new PersonRequestDto(
                    "456.113.470-02",
                    "PF",
                    "Fornecedor Dois"
                ),

                new IndividualPersonRequestDto(
                    "22222222"
                ),

                null,

                List.of(),
                List.of(),

                "45 DIAS",
                10,
                new BigDecimal("2000.00"),
                "Equipamentos",
                "Segundo fornecedor",

                null,

                List.of(),
                List.of(),

                true
            )
        );

        assertThat(primeiro.id())
            .isNotEqualTo(segundo.id());

        // ============================================================
        // 3. Tenta alterar o segundo para o CPF do primeiro
        // ============================================================

        SupplierRequestDto alteracao = new SupplierRequestDto(

            new PersonRequestDto(
                "115.725.240-06",
                "PF",
                "Fornecedor Dois Alterado"
            ),

            new IndividualPersonRequestDto(
                "99999999"
            ),

            null,

            List.of(),
            List.of(),

            "60 DIAS",
            15,
            new BigDecimal("3000.00"),
            "Outro",
            "Tentativa de reutilizar CPF",

            null,

            List.of(),
            List.of(),

            true
        );

        // ============================================================
        // 4. Deve bloquear
        // ============================================================

        assertThatThrownBy(() ->
            supplierService.update(segundo.id(), alteracao)
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "CPF/CNPJ já cadastrado para outra pessoa no sistema."
            );

        // ============================================================
        // 5. Garante que o segundo fornecedor não foi alterado
        // ============================================================

        entityManager.flush();
        entityManager.clear();

        Supplier segundoDepois =
            supplierRepository
                .findByIdWithPerson(segundo.id())
                .orElseThrow();

        // CPF original do segundo fornecedor,
        // normalizado e sem máscara
        assertThat(segundoDepois.getPerson().getCpfCnpj())
            .isEqualTo("45611347002");

        assertThat(segundoDepois.getPerson().getName())
            .isEqualTo("Fornecedor Dois");
    }




    @Test
    void devePermitirManterMesmoCpfCnpjAoAtualizarFornecedor() {

        // ============================================================
        // 1. Cria fornecedor
        // ============================================================

        SupplierResponseDto criado = supplierService.create(
            new SupplierRequestDto(

                new PersonRequestDto(
                    "115.725.240-06",
                    "PF",
                    "Fornecedor Original"
                ),

                new IndividualPersonRequestDto(
                    "11111111"
                ),

                null,

                List.of(),
                List.of(),

                "30 DIAS",
                7,
                new BigDecimal("1000.00"),
                "Material",
                "Cadastro original",

                null,

                List.of(),
                List.of(),

                true
            )
        );

        // ============================================================
        // 2. Atualiza mantendo o próprio CPF
        // ============================================================

        SupplierRequestDto alteracao = new SupplierRequestDto(

            new PersonRequestDto(
                "115.725.240-06",
                "PF",
                "Fornecedor Atualizado"
            ),

            new IndividualPersonRequestDto(
                "99999999"
            ),

            null,

            List.of(),
            List.of(),

            "60 DIAS",
            15,
            new BigDecimal("2500.00"),
            "Equipamentos",
            "Fornecedor atualizado sem alterar CPF",

            null,

            List.of(),
            List.of(),

            true
        );

        SupplierResponseDto atualizado =
            supplierService.update(
                criado.id(),
                alteracao
            );

        // ============================================================
        // 3. Deve permitir
        // ============================================================

        assertThat(atualizado).isNotNull();
        assertThat(atualizado.id())
            .isEqualTo(criado.id());

        // ============================================================
        // 4. Recarrega
        // ============================================================

        entityManager.flush();
        entityManager.clear();

        Supplier supplier =
            supplierRepository
                .findByIdWithPerson(criado.id())
                .orElseThrow();

        // ============================================================
        // 5. CPF continua o mesmo
        // ============================================================

        assertThat(supplier.getPerson().getCpfCnpj())
            .isEqualTo("11572524006");

        // ============================================================
        // 6. Demais dados foram atualizados
        // ============================================================

        assertThat(supplier.getPerson().getName())
            .isEqualTo("Fornecedor Atualizado");

        assertThat(supplier.getPrazoEntregaDias())
            .isEqualTo(15);

        assertThat(supplier.getValorMinimoPedido())
            .isEqualByComparingTo("2500.00");

        assertThat(supplier.getCategoria())
            .isEqualTo("Equipamentos");
    }


}