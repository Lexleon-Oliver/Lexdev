package net.ddns.lexdev.systempro_api.service;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.domain.Client;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.PersonAddress;
import net.ddns.lexdev.systempro_api.domain.PersonContact;
import net.ddns.lexdev.systempro_api.dto.ClientRequestDto;
import net.ddns.lexdev.systempro_api.dto.ClientResponseDto;
import net.ddns.lexdev.systempro_api.dto.IndividualPersonRequestDto;
import net.ddns.lexdev.systempro_api.dto.PersonAddressRequestDto;
import net.ddns.lexdev.systempro_api.dto.PersonContactRequestDto;
import net.ddns.lexdev.systempro_api.dto.PersonRequestDto;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.integration.IntegrationTestBase;
import net.ddns.lexdev.systempro_api.repository.ClientRepository;

class ClientServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ClientService clientService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void deveCadastrarClienteComPersonContatosEEndereco() {

        // ------------------------------------------------------------
        // Arrange
        // ------------------------------------------------------------

        ClientRequestDto dto = new ClientRequestDto(

            new PersonRequestDto(
                "529.982.247-25",
                "PF",
                "Maria da Silva"
            ),

            new IndividualPersonRequestDto(
                "MG-12.345.678"
            ),

            null,

            List.of(
                new PersonContactRequestDto(
                    "EMAIL",
                    "  MARIA@EXEMPLO.COM  ",
                    true,
                    "E-mail principal"
                ),
                new PersonContactRequestDto(
                    "CELULAR",
                    "(31) 99999-8888",
                    false,
                    "Celular pessoal"
                )
            ),

            List.of(
                new PersonAddressRequestDto(
                    "RESIDENCIAL",
                    "36.400-000",
                    "Rua dos Testes",
                    "100",
                    "Apto 201",
                    "Centro",
                    "Barbacena",
                    " mg ",
                    true
                )
            ),

            true
        );

        // ------------------------------------------------------------
        // Act
        // ------------------------------------------------------------

        ClientResponseDto response = clientService.create(dto);

        assertThat(response).isNotNull();

        /*
         * Garante que tudo foi efetivamente enviado ao PostgreSQL.
         */
        entityManager.flush();

        /*
         * Localiza o ID diretamente no banco.
         *
         * Dessa forma o teste não depende da estrutura interna
         * de ClientResponseDto.
         */
        Long clientId =
            ((Number) entityManager.createNativeQuery("""
                SELECT c.id
                FROM tb_client c
                JOIN tb_person p ON p.id = c.person_id
                WHERE p.cpf_cnpj = :cpfCnpj
                """)
                .setParameter("cpfCnpj", "52998224725")
                .getSingleResult())
            .longValue();

        /*
         * Limpa o persistence context.
         *
         * As entidades abaixo terão de ser carregadas novamente
         * pelo Hibernate.
         */
        entityManager.clear();

        // ------------------------------------------------------------
        // Assert - Client
        // ------------------------------------------------------------

        Client persistedClient =
            entityManager.find(Client.class, clientId);

        assertThat(persistedClient).isNotNull();
        assertThat(persistedClient.isActive()).isTrue();

        // ------------------------------------------------------------
        // Assert - Person
        // ------------------------------------------------------------

        Person persistedPerson =
            persistedClient.getPerson();

        assertThat(persistedPerson).isNotNull();

        assertThat(persistedPerson.getName())
            .isEqualTo("Maria da Silva");

        assertThat(persistedPerson.getCpfCnpj())
            .isEqualTo("52998224725");

        assertThat(persistedPerson.getTipoPessoa().name())
            .isEqualTo("PF");

        assertThat(persistedPerson.isActive())
            .isTrue();

        // ------------------------------------------------------------
        // Assert - IndividualPerson
        // ------------------------------------------------------------

        assertThat(persistedPerson.getIndividualPerson())
            .isNotNull();

        assertThat(persistedPerson.getIndividualPerson().getRg())
            .isEqualTo("12345678");

        // ------------------------------------------------------------
        // Assert - Contatos
        // ------------------------------------------------------------

        assertThat(persistedPerson.getContacts())
            .hasSize(2);

        PersonContact email =
            persistedPerson.getContacts()
                .stream()
                .filter(contact ->
                    contact.getType().name().equals("EMAIL"))
                .findFirst()
                .orElseThrow();

        assertThat(email.getValue())
            .isEqualTo("maria@exemplo.com");

        assertThat(email.isPrincipal())
            .isTrue();

        PersonContact celular =
            persistedPerson.getContacts()
                .stream()
                .filter(contact ->
                    contact.getType().name().equals("CELULAR"))
                .findFirst()
                .orElseThrow();

        assertThat(celular.getValue())
            .isEqualTo("31999998888");

        assertThat(celular.isPrincipal())
            .isFalse();

        // ------------------------------------------------------------
        // Assert - Endereço
        // ------------------------------------------------------------

        assertThat(persistedPerson.getAddresses())
            .hasSize(1);

        PersonAddress address =
            persistedPerson.getAddresses()
                .get(0);

        assertThat(address.getType().name())
            .isEqualTo("RESIDENCIAL");

        assertThat(address.getCep())
            .isEqualTo("36400000");

        assertThat(address.getLogradouro())
            .isEqualTo("Rua dos Testes");

        assertThat(address.getNumero())
            .isEqualTo("100");

        assertThat(address.getComplemento())
            .isEqualTo("Apto 201");

        assertThat(address.getBairro())
            .isEqualTo("Centro");

        assertThat(address.getCidade())
            .isEqualTo("Barbacena");

        assertThat(address.getUf())
            .isEqualTo("MG");

        assertThat(address.isPrincipal())
            .isTrue();

        // ------------------------------------------------------------
        // Assert - relacionamento
        // ------------------------------------------------------------

        assertThat(email.getPerson().getId())
            .isEqualTo(persistedPerson.getId());

        assertThat(celular.getPerson().getId())
            .isEqualTo(persistedPerson.getId());

        assertThat(address.getPerson().getId())
            .isEqualTo(persistedPerson.getId());
    }

    @Test
    @Transactional
    void naoDeveCadastrarClienteComCpfCnpjJaUtilizadoPorClienteAtivo() {

        String cpfCnpj = "529.982.247-25";

        // ------------------------------------------------------------
        // Primeiro cadastro
        // ------------------------------------------------------------

        ClientRequestDto primeiroDto = new ClientRequestDto(
            new PersonRequestDto(
                cpfCnpj,
                "PF",
                "Maria da Silva"
            ),
            new IndividualPersonRequestDto("MG-12.345.678"),
            null,
            List.of(
                new PersonContactRequestDto(
                    "EMAIL",
                    "maria@exemplo.com",
                    true,
                    "E-mail principal"
                )
            ),
            List.of(
                new PersonAddressRequestDto(
                    "RESIDENCIAL",
                    "36.400-000",
                    "Rua dos Testes",
                    "100",
                    null,
                    "Centro",
                    "Barbacena",
                    "MG",
                    true
                )
            ),
            true
        );

        ClientResponseDto primeiro =
            clientService.create(primeiroDto);

        assertThat(primeiro).isNotNull();

        entityManager.flush();

        // ------------------------------------------------------------
        // Segundo cadastro com o mesmo CPF/CNPJ
        // ------------------------------------------------------------

        ClientRequestDto segundoDto = new ClientRequestDto(
            new PersonRequestDto(
                cpfCnpj,
                "PF",
                "Outra Pessoa"
            ),
            new IndividualPersonRequestDto("MG-99.999.999"),
            null,
            List.of(
                new PersonContactRequestDto(
                    "EMAIL",
                    "outra@exemplo.com",
                    true,
                    "E-mail"
                )
            ),
            List.of(
                new PersonAddressRequestDto(
                    "RESIDENCIAL",
                    "36.400-000",
                    "Outra Rua",
                    "200",
                    null,
                    "Centro",
                    "Barbacena",
                    "MG",
                    true
                )
            ),
            true
        );

        assertThatThrownBy(() ->
            clientService.create(segundoDto)
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "Esta pessoa/empresa já está cadastrada como cliente ativo."
            );

        // ------------------------------------------------------------
        // Garante que continuamos com apenas um registro
        // ------------------------------------------------------------

        entityManager.flush();

        Number totalPersons =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person
                WHERE cpf_cnpj = :cpfCnpj
                """)
                .setParameter("cpfCnpj", "52998224725")
                .getSingleResult();

        Number totalClients =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_client c
                JOIN tb_person p ON p.id = c.person_id
                WHERE p.cpf_cnpj = :cpfCnpj
                """)
                .setParameter("cpfCnpj", "52998224725")
                .getSingleResult();

        assertThat(totalPersons.longValue())
            .isEqualTo(1);

        assertThat(totalClients.longValue())
            .isEqualTo(1);
    }

    @Test
    @Transactional
    void deveRealizarSoftDeleteDoCliente() {

        // ------------------------------------------------------------
        // Arrange
        // ------------------------------------------------------------

        String cpfCnpj = "39053344705";

        ClientRequestDto dto = new ClientRequestDto(
            new PersonRequestDto(
                cpfCnpj,
                "PF",
                "João da Silva"
            ),
            new IndividualPersonRequestDto("MG-11.111.111"),
            null,
            List.of(
                new PersonContactRequestDto(
                    "EMAIL",
                    "joao@exemplo.com",
                    true,
                    "E-mail principal"
                )
            ),
            List.of(
                new PersonAddressRequestDto(
                    "RESIDENCIAL",
                    "36.400-000",
                    "Rua do Teste",
                    "10",
                    null,
                    "Centro",
                    "Barbacena",
                    "MG",
                    true
                )
            ),
            true
        );

        clientService.create(dto);

        entityManager.flush();
        entityManager.clear();

        Long clientId =
            ((Number) entityManager.createNativeQuery("""
                SELECT c.id
                FROM tb_client c
                JOIN tb_person p ON p.id = c.person_id
                WHERE p.cpf_cnpj = :cpfCnpj
                """)
                .setParameter("cpfCnpj", "39053344705")
                .getSingleResult())
            .longValue();

        // ------------------------------------------------------------
        // Act
        // ------------------------------------------------------------

        clientService.delete(clientId);

        entityManager.flush();
        entityManager.clear();

        // ------------------------------------------------------------
        // Assert - banco
        //
        // O registro deve continuar existindo fisicamente,
        // mas com active = false.
        // ------------------------------------------------------------

        Number active =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_client
                WHERE id = :id
                AND active = true
                """)
                .setParameter("id", clientId)
                .getSingleResult();

        Number inactive =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_client
                WHERE id = :id
                AND active = false
                """)
                .setParameter("id", clientId)
                .getSingleResult();

        assertThat(active.longValue())
            .isZero();

        assertThat(inactive.longValue())
            .isEqualTo(1);

        // ------------------------------------------------------------
        // Assert - @SQLRestriction
        //
        // Consultas JPA normais não devem enxergar o cliente inativo.
        // ------------------------------------------------------------

        assertThatThrownBy(() ->
            clientService.findById(clientId)
        )
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage(
                "Cliente não encontrado com o ID: " + clientId
            );

        // ------------------------------------------------------------
        // Assert - Person
        //
        // O ClientService.delete() atual não desativa a Person.
        // Portanto, vamos verificar explicitamente o comportamento.
        // ------------------------------------------------------------

        Number activePerson =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person
                WHERE cpf_cnpj = :cpfCnpj
                AND active = true
                """)
                .setParameter("cpfCnpj", "39053344705")
                .getSingleResult();

        assertThat(activePerson.longValue())
            .isEqualTo(1);
    }

    @Test
    void deveImpedirNovoCadastroQuandoClienteAnteriorFoiExcluido() {

        String cpfCnpj = "390.533.447-05";

        ClientRequestDto dto = new ClientRequestDto(
            new PersonRequestDto(
                cpfCnpj,
                "PF",
                "João da Silva"
            ),
            new IndividualPersonRequestDto("MG-11.111.111"),
            null,
            List.of(
                new PersonContactRequestDto(
                    "EMAIL",
                    "joao@exemplo.com",
                    true,
                    "E-mail principal"
                )
            ),
            List.of(
                new PersonAddressRequestDto(
                    "RESIDENCIAL",
                    "36.400-000",
                    "Rua do Teste",
                    "10",
                    null,
                    "Centro",
                    "Barbacena",
                    "MG",
                    true
                )
            ),
            true
        );

        // Primeiro cadastro
        clientService.create(dto);

        Long clientId =
            ((Number) entityManager.createNativeQuery("""
                SELECT c.id
                FROM tb_client c
                JOIN tb_person p ON p.id = c.person_id
                WHERE p.cpf_cnpj = :cpfCnpj
                """)
                .setParameter("cpfCnpj", "39053344705")
                .getSingleResult())
            .longValue();

        // Soft delete
        clientService.delete(clientId);

        // Nova tentativa
        assertThatThrownBy(() ->
            clientService.create(dto)
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "Esta pessoa/empresa possui um cadastro de cliente inativo. "
                + "A reativação deve ser realizada pelo suporte."
            );

        // Não deve existir outro Client
        Number totalClients =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_client c
                JOIN tb_person p ON p.id = c.person_id
                WHERE p.cpf_cnpj = :cpfCnpj
                """)
                .setParameter("cpfCnpj", "39053344705")
                .getSingleResult();

        assertThat(totalClients.longValue())
            .isEqualTo(1);

        // O cadastro original continua inativo
        Number inactiveClients =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_client c
                JOIN tb_person p ON p.id = c.person_id
                WHERE p.cpf_cnpj = :cpfCnpj
                AND c.active = false
                """)
                .setParameter("cpfCnpj", "39053344705")
                .getSingleResult();

        assertThat(inactiveClients.longValue())
            .isEqualTo(1);
    }

    @Test
    @Transactional
    void deveAtualizarClienteEsubstituirContatosEEndereco() {

        // ============================================================
        // CADASTRO INICIAL
        // ============================================================

        String cpfCnpj = "529.982.247-25";

        ClientRequestDto cadastroInicial = new ClientRequestDto(
            new PersonRequestDto(
                cpfCnpj,
                "PF",
                "Maria da Silva"
            ),
            new IndividualPersonRequestDto(
                "12345678"
            ),
            null,
            List.of(
                new PersonContactRequestDto(
                    "EMAIL",
                    "maria.antigo@exemplo.com",
                    true,
                    "E-mail antigo"
                ),
                new PersonContactRequestDto(
                    "CELULAR",
                    "(31) 99999-1111",
                    false,
                    "Celular antigo"
                )
            ),
            List.of(
                new PersonAddressRequestDto(
                    "RESIDENCIAL",
                    "36.400-000",
                    "Rua Antiga",
                    "10",
                    null,
                    "Centro",
                    "Barbacena",
                    "MG",
                    true
                )
            ),
            true
        );

        clientService.create(cadastroInicial);

        entityManager.flush();

        Long clientId =
            ((Number) entityManager.createNativeQuery("""
                SELECT c.id
                FROM tb_client c
                JOIN tb_person p ON p.id = c.person_id
                WHERE p.cpf_cnpj = :cpfCnpj
                """)
                .setParameter("cpfCnpj", "52998224725")
                .getSingleResult())
            .longValue();

        // ============================================================
        // ATUALIZAÇÃO
        // ============================================================

        ClientRequestDto cadastroAtualizado = new ClientRequestDto(
            new PersonRequestDto(
                cpfCnpj,
                "PF",
                "Maria da Silva Atualizada"
            ),
            new IndividualPersonRequestDto(
                "87654321"
            ),
            null,
            List.of(
                new PersonContactRequestDto(
                    "EMAIL",
                    "maria.novo@exemplo.com",
                    true,
                    "Novo e-mail"
                ),
                new PersonContactRequestDto(
                    "WHATSAPP",
                    "(31) 98888-7777",
                    false,
                    "WhatsApp"
                )
            ),
            List.of(
                new PersonAddressRequestDto(
                    "RESIDENCIAL",
                    "36.401-000",
                    "Rua Nova",
                    "200",
                    "Apto 301",
                    "Centro",
                    "Barbacena",
                    "MG",
                    true
                )
            ),
            true
        );

        clientService.update(clientId, cadastroAtualizado);

        entityManager.flush();
        entityManager.clear();

        // ============================================================
        // VERIFICA PERSON
        // ============================================================

        Person person =
            (Person) entityManager.createQuery("""
                SELECT c.person
                FROM Client c
                WHERE c.id = :id
                """)
                .setParameter("id", clientId)
                .getSingleResult();

        assertThat(person.getName())
            .isEqualTo("Maria da Silva Atualizada");

        assertThat(person.getCpfCnpj())
            .isEqualTo("52998224725");

        assertThat(person.getIndividualPerson())
            .isNotNull();

        assertThat(person.getIndividualPerson().getRg())
            .isEqualTo("87654321");

        // ============================================================
        // CONTATOS
        // ============================================================

        Number totalContacts =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person_contact
                WHERE person_id = :personId
                """)
                .setParameter("personId", person.getId())
                .getSingleResult();

        assertThat(totalContacts.longValue())
            .isEqualTo(2);

        Number oldEmail =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person_contact
                WHERE person_id = :personId
                AND value = :value
                """)
                .setParameter("personId", person.getId())
                .setParameter("value", "maria.antigo@exemplo.com")
                .getSingleResult();

        assertThat(oldEmail.longValue())
            .isZero();

        Number oldPhone =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person_contact
                WHERE person_id = :personId
                AND value = :value
                """)
                .setParameter("personId", person.getId())
                .setParameter("value", "31999991111")
                .getSingleResult();

        assertThat(oldPhone.longValue())
            .isZero();

        Number newEmail =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person_contact
                WHERE person_id = :personId
                AND value = :value
                """)
                .setParameter("personId", person.getId())
                .setParameter("value", "maria.novo@exemplo.com")
                .getSingleResult();

        assertThat(newEmail.longValue())
            .isEqualTo(1);

        Number newWhatsapp =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person_contact
                WHERE person_id = :personId
                AND value = :value
                """)
                .setParameter("personId", person.getId())
                .setParameter("value", "31988887777")
                .getSingleResult();

        assertThat(newWhatsapp.longValue())
            .isEqualTo(1);

        // ============================================================
        // ENDEREÇO
        // ============================================================

        Number totalAddresses =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person_address
                WHERE person_id = :personId
                """)
                .setParameter("personId", person.getId())
                .getSingleResult();

        assertThat(totalAddresses.longValue())
            .isEqualTo(1);

        Number oldAddress =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person_address
                WHERE person_id = :personId
                AND logradouro = :logradouro
                """)
                .setParameter("personId", person.getId())
                .setParameter("logradouro", "Rua Antiga")
                .getSingleResult();

        assertThat(oldAddress.longValue())
            .isZero();

        Number newAddress =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person_address
                WHERE person_id = :personId
                AND logradouro = :logradouro
                """)
                .setParameter("personId", person.getId())
                .setParameter("logradouro", "Rua Nova")
                .getSingleResult();

        assertThat(newAddress.longValue())
            .isEqualTo(1);
    }

    @Test
    @Transactional
    void deveAtualizarMantendoAlgunsRelacionamentosEAdicionandoOutros() {

        String cpfCnpj = "529.982.247-25";

        // ============================================================
        // CADASTRO INICIAL
        // ============================================================

        ClientRequestDto inicial = new ClientRequestDto(
            new PersonRequestDto(
                cpfCnpj,
                "PF",
                "Ana Souza"
            ),
            new IndividualPersonRequestDto("12345678"),
            null,
            List.of(
                new PersonContactRequestDto(
                    "EMAIL",
                    "ana@exemplo.com",
                    true,
                    "E-mail"
                ),
                new PersonContactRequestDto(
                    "CELULAR",
                    "(31) 99999-1111",
                    false,
                    "Celular"
                )
            ),
            List.of(
                new PersonAddressRequestDto(
                    "RESIDENCIAL",
                    "36.400-000",
                    "Rua A",
                    "10",
                    null,
                    "Centro",
                    "Barbacena",
                    "MG",
                    true
                ),
                new PersonAddressRequestDto(
                    "COMERCIAL",
                    "36.401-000",
                    "Rua B",
                    "20",
                    null,
                    "Centro",
                    "Barbacena",
                    "MG",
                    false
                )
            ),
            true
        );

        clientService.create(inicial);

        entityManager.flush();

        Long clientId =
            ((Number) entityManager.createNativeQuery("""
                SELECT c.id
                FROM tb_client c
                JOIN tb_person p ON p.id = c.person_id
                WHERE p.cpf_cnpj = :cpfCnpj
                """)
                .setParameter("cpfCnpj", "52998224725")
                .getSingleResult())
            .longValue();

        Long personId =
            ((Number) entityManager.createNativeQuery("""
                SELECT p.id
                FROM tb_person p
                WHERE p.cpf_cnpj = :cpfCnpj
                """)
                .setParameter("cpfCnpj", "52998224725")
                .getSingleResult())
            .longValue();

        // ============================================================
        // ATUALIZAÇÃO PARCIAL
        // ============================================================

        ClientRequestDto atualizado = new ClientRequestDto(
            new PersonRequestDto(
                cpfCnpj,
                "PF",
                "Ana Souza Atualizada"
            ),
            new IndividualPersonRequestDto("87654321"),
            null,
            List.of(
                new PersonContactRequestDto(
                    "EMAIL",
                    "ana@exemplo.com",
                    true,
                    "E-mail mantido"
                ),
                new PersonContactRequestDto(
                    "WHATSAPP",
                    "(31) 98888-7777",
                    false,
                    "WhatsApp novo"
                )
            ),
            List.of(
                new PersonAddressRequestDto(
                    "RESIDENCIAL",
                    "36.400-000",
                    "Rua A",
                    "10",
                    null,
                    "Centro",
                    "Barbacena",
                    "MG",
                    true
                )
            ),
            true
        );

        clientService.update(clientId, atualizado);

        entityManager.flush();
        entityManager.clear();

        // ============================================================
        // CLIENT E PERSON DEVEM SER OS MESMOS
        // ============================================================

        Number currentPersonId =
            (Number) entityManager.createNativeQuery("""
                SELECT p.id
                FROM tb_person p
                JOIN tb_client c ON c.person_id = p.id
                WHERE c.id = :clientId
                """)
                .setParameter("clientId", clientId)
                .getSingleResult();

        assertThat(currentPersonId.longValue())
            .isEqualTo(personId);

        // ============================================================
        // DADOS DA PERSON
        // ============================================================

        Person person =
            (Person) entityManager.createQuery("""
                SELECT c.person
                FROM Client c
                WHERE c.id = :id
                """)
                .setParameter("id", clientId)
                .getSingleResult();

        assertThat(person.getName())
            .isEqualTo("Ana Souza Atualizada");

        assertThat(person.getCpfCnpj())
            .isEqualTo("52998224725");

        assertThat(person.getIndividualPerson())
            .isNotNull();

        assertThat(person.getIndividualPerson().getRg())
            .isEqualTo("87654321");

        // ============================================================
        // CONTATOS
        // ============================================================

        Number totalContacts =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person_contact
                WHERE person_id = :personId
                """)
                .setParameter("personId", personId)
                .getSingleResult();

        assertThat(totalContacts.longValue())
            .isEqualTo(2);

        Number email =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person_contact
                WHERE person_id = :personId
                AND type = 'EMAIL'
                AND value = 'ana@exemplo.com'
                """)
                .setParameter("personId", personId)
                .getSingleResult();

        assertThat(email.longValue())
            .isEqualTo(1);

        Number celular =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person_contact
                WHERE person_id = :personId
                AND type = 'CELULAR'
                """)
                .setParameter("personId", personId)
                .getSingleResult();

        assertThat(celular.longValue())
            .isZero();

        Number whatsapp =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person_contact
                WHERE person_id = :personId
                AND type = 'WHATSAPP'
                AND value = '31988887777'
                """)
                .setParameter("personId", personId)
                .getSingleResult();

        assertThat(whatsapp.longValue())
            .isEqualTo(1);

        // ============================================================
        // ENDEREÇOS
        // ============================================================

        Number totalAddresses =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person_address
                WHERE person_id = :personId
                """)
                .setParameter("personId", personId)
                .getSingleResult();

        assertThat(totalAddresses.longValue())
            .isEqualTo(1);

        Number residencial =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person_address
                WHERE person_id = :personId
                AND type = 'RESIDENCIAL'
                AND logradouro = 'Rua A'
                """)
                .setParameter("personId", personId)
                .getSingleResult();

        assertThat(residencial.longValue())
            .isEqualTo(1);

        Number comercial =
            (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM tb_person_address
                WHERE person_id = :personId
                AND type = 'COMERCIAL'
                """)
                .setParameter("personId", personId)
                .getSingleResult();

        assertThat(comercial.longValue())
            .isZero();
    }

    @Test
    @Transactional
    void deveCarregarClientEPersonAposConsultarNoBanco() {

        // ============================================================
        // Cadastro
        // ============================================================

        String cpfCnpj = "529.982.247-25";

        ClientRequestDto dto = new ClientRequestDto(
            new PersonRequestDto(
                cpfCnpj,
                "PF",
                "Carlos Oliveira"
            ),
            new IndividualPersonRequestDto("12345678"),
            null,
            List.of(
                new PersonContactRequestDto(
                    "EMAIL",
                    "carlos@exemplo.com",
                    true,
                    "E-mail principal"
                ),
                new PersonContactRequestDto(
                    "CELULAR",
                    "(31) 99999-1111",
                    false,
                    "Celular"
                )
            ),
            List.of(
                new PersonAddressRequestDto(
                    "RESIDENCIAL",
                    "36.400-000",
                    "Rua A",
                    "10",
                    null,
                    "Centro",
                    "Barbacena",
                    "MG",
                    true
                )
            ),
            true
        );

        clientService.create(dto);

        entityManager.flush();

        Long clientId =
            ((Number) entityManager.createNativeQuery("""
                SELECT c.id
                FROM tb_client c
                JOIN tb_person p ON p.id = c.person_id
                WHERE p.cpf_cnpj = :cpfCnpj
                """)
                .setParameter("cpfCnpj", "52998224725")
                .getSingleResult())
            .longValue();

        // ------------------------------------------------------------
        // Limpa o contexto para garantir uma nova consulta
        // ------------------------------------------------------------

        entityManager.clear();

        // ============================================================
        // Consulta usando o repository real
        // ============================================================

        Client client =
            clientRepository.findByIdWithPerson(clientId)
                .orElseThrow();

        // ============================================================
        // Client
        // ============================================================

        assertThat(client).isNotNull();
        assertThat(client.getId()).isEqualTo(clientId);
        assertThat(client.isActive()).isTrue();

        // ============================================================
        // Person
        // ============================================================

        Person person = client.getPerson();

        assertThat(person).isNotNull();
        assertThat(person.getId()).isNotNull();

        assertThat(person.getName())
            .isEqualTo("Carlos Oliveira");

        assertThat(person.getCpfCnpj())
            .isEqualTo("52998224725");

        // ============================================================
        // Contacts
        //
        // A coleção é LAZY.
        // Como estamos dentro da transação, devemos conseguir
        // carregá-la através do relacionamento.
        // ============================================================

        assertThat(person.getContacts())
            .hasSize(2);

        assertThat(person.getContacts())
            .extracting(PersonContact::getValue)
            .containsExactlyInAnyOrder(
                "carlos@exemplo.com",
                "31999991111"
            );

        // ============================================================
        // Addresses
        // ============================================================

        assertThat(person.getAddresses())
            .hasSize(1);

        PersonAddress address =
            person.getAddresses().get(0);

        assertThat(address.getCep())
            .isEqualTo("36400000");

        assertThat(address.getLogradouro())
            .isEqualTo("Rua A");

        assertThat(address.getUf())
            .isEqualTo("MG");

        assertThat(address.getPerson().getId())
            .isEqualTo(person.getId());
    }

    @Test
    @Transactional
    void deveRetornarSomenteClientesAtivosNaConsultaPaginada() {

        // ============================================================
        // Cadastro de 3 clientes
        // ============================================================

        clientService.create(new ClientRequestDto(
            new PersonRequestDto(
                "971.964.840-61",
                "PF",
                "Cliente Ativo 1"
            ),
            new IndividualPersonRequestDto("11111111"),
            null,
            List.of(),
            List.of(),
            true
        ));

        clientService.create(new ClientRequestDto(
            new PersonRequestDto(
                "617.034.430-04",
                "PF",
                "Cliente que será inativado"
            ),
            new IndividualPersonRequestDto("22222222"),
            null,
            List.of(),
            List.of(),
            true
        ));

        clientService.create(new ClientRequestDto(
            new PersonRequestDto(
                "022.495.950-62",
                "PF",
                "Cliente Ativo 2"
            ),
            new IndividualPersonRequestDto("33333333"),
            null,
            List.of(),
            List.of(),
            true
        ));

        entityManager.flush();
        entityManager.clear();
        // ============================================================
        // Descobre o ID do segundo cliente diretamente no banco
        // ============================================================

        Long clienteInativoId =
            ((Number) entityManager.createNativeQuery("""
                SELECT c.id
                FROM tb_client c
                JOIN tb_person p ON p.id = c.person_id
                WHERE p.cpf_cnpj = :cpfCnpj
                """)
                .setParameter("cpfCnpj", "61703443004")
                .getSingleResult())
            .longValue();

        // ============================================================
        // Inativa o segundo cliente
        // ============================================================

        clientService.delete(clienteInativoId);

        entityManager.flush();
        entityManager.clear();

        // ============================================================
        // Consulta paginada
        // ============================================================

        Page<ClientResponseDto> resultado =
            clientService.findAll(PageRequest.of(0, 10));

        // ============================================================
        // Assertions
        // ============================================================

        assertThat(resultado).isNotNull();

        assertThat(resultado.getTotalElements())
            .isEqualTo(2);

        assertThat(resultado.getContent())
            .hasSize(2);

        assertThat(resultado.getNumber())
            .isZero();

        assertThat(resultado.getSize())
            .isEqualTo(10);

        assertThat(resultado.getTotalPages())
            .isEqualTo(1);
    }
}
