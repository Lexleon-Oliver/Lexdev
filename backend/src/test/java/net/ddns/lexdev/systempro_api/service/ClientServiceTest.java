package net.ddns.lexdev.systempro_api.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.domain.Client;
import net.ddns.lexdev.systempro_api.domain.IndividualPerson;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.PersonAddress;
import net.ddns.lexdev.systempro_api.domain.PersonContact;
import net.ddns.lexdev.systempro_api.dto.ClientRequestDto;
import net.ddns.lexdev.systempro_api.dto.ClientResponseDto;
import net.ddns.lexdev.systempro_api.dto.IndividualPersonRequestDto;
import net.ddns.lexdev.systempro_api.dto.LegalEntityRequestDto;
import net.ddns.lexdev.systempro_api.dto.PersonAddressRequestDto;
import net.ddns.lexdev.systempro_api.dto.PersonContactRequestDto;
import net.ddns.lexdev.systempro_api.dto.PersonRequestDto;
import net.ddns.lexdev.systempro_api.enums.AddressType;
import net.ddns.lexdev.systempro_api.enums.ContactType;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.ClientRepository;



@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private PersonService personService;

    @InjectMocks
    private ClientService service;

    private ClientRequestDto buildClientRequestDto(String cpfCnpj) {

        PersonRequestDto personDto =
            new PersonRequestDto(
                cpfCnpj,
                "PF",
                "João da Silva"
            );

        IndividualPersonRequestDto individual =
            new IndividualPersonRequestDto(
                "495493478"
            );

        return new ClientRequestDto(
            personDto,
            individual,
            null,
            List.of(
                new PersonContactRequestDto(
                    "EMAIL",
                    "joao@email.com",
                    true,
                    null
                ),
                new PersonContactRequestDto(
                    "WHATSAPP",
                    "31999999999",
                    false,
                    null
                )
            ),
            List.of(
                new PersonAddressRequestDto(
                    "RESIDENCIAL",
                    "36200-000",
                    "Rua A",
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
    }

    private Person buildPerson(
        Long id,
        String cpfCnpj
    ) {
        Person person = new Person();

        ReflectionTestUtils.setField(
            person,
            "id",
            id
        );

        person.setTipoPessoa(TipoPessoa.PF);
        person.setName("João da Silva");
        person.setCpfCnpj(cpfCnpj);
        person.setActive(true);

        IndividualPerson individual = new IndividualPerson();
        individual.setPerson(person);
        individual.setRg("495493478");

        person.setIndividualPerson(individual);

        return person;
    }

    private Client buildClient(
        Long id,
        Person person
    ) {

        Client client = new Client();

        ReflectionTestUtils.setField(
            client,
            "id",
            id
        );

        client.setPerson(person);
        client.setActive(true);

        return client;
    }

    private ClientResponseDto buildClientResponse(
        Client client
    ) {

        return ClientResponseDto.fromEntity(client);
    }

    @Test
    @DisplayName("Deve criar cliente com CPF/CNPJ sanitizado")
    void deveCriarClienteComCpfCnpjSanitizado() {

        ClientRequestDto dto =
            buildClientRequestDto("123.456.789-00");

        Person person =
            buildPerson(
                10L,
                "12345678900"
            );

        Client clientSalvo =
            buildClient(
                1L,
                person
            );

        when(
            clientRepository.existsByPersonCpfCnpj(
                "12345678900"
            )
        ).thenReturn(false);

        when(
            personService.getOrCreateForRegistration(
                "12345678900"
            )
        ).thenReturn(person);

        doNothing()
        .when(personService)
        .updateFromDto(
            any(Person.class),
            any(PersonRequestDto.class),
            any(IndividualPersonRequestDto.class),
            isNull(LegalEntityRequestDto.class)
        );

        when(
            clientRepository.save(
                any(Client.class)
            )
        ).thenReturn(clientSalvo);

        ClientResponseDto result =
            service.create(dto);

        ArgumentCaptor<Client> clientCaptor =
            ArgumentCaptor.forClass(Client.class);

        verify(clientRepository)
            .save(clientCaptor.capture());

        Client clientEnviadoParaRepository =
            clientCaptor.getValue();

        assertThat(
            clientEnviadoParaRepository
                .getPerson()
                .getCpfCnpj()
        )
            .isEqualTo("12345678900");

        assertThat(result.id())
            .isEqualTo(1L);

        assertThat(result.person().id())
            .isEqualTo(10L);

        assertThat(result.person().tipoPessoa())
            .isEqualTo("PF");

        assertThat(result.person().name())
            .isEqualTo("João da Silva");

        assertThat(result.person().cpfCnpj())
            .isEqualTo("12345678900");

        assertThat(result.individual())
            .isNotNull();

        assertThat(result.individual().rg())
            .isEqualTo("495493478");

        assertThat(person.getContacts())
        .hasSize(2);

        assertThat(person.getContacts().get(0).getType())
            .isEqualTo(ContactType.EMAIL);

        assertThat(person.getContacts().get(0).getValue())
            .isEqualTo("joao@email.com");

        assertThat(person.getContacts().get(0).isPrincipal())
            .isTrue();

        assertThat(person.getContacts().get(0).getPerson())
            .isSameAs(person);

        assertThat(person.getContacts().get(1).getType())
            .isEqualTo(ContactType.WHATSAPP);

        assertThat(person.getContacts().get(1).getValue())
            .isEqualTo("31999999999");

        assertThat(person.getContacts().get(1).isPrincipal())
            .isFalse();

        assertThat(person.getContacts().get(1).getPerson())
            .isSameAs(person);

        assertThat(person.getAddresses())
            .hasSize(1);

        assertThat(person.getAddresses().get(0).getType())
            .isEqualTo(AddressType.RESIDENCIAL);

        assertThat(person.getAddresses().get(0).getCep())
            .isEqualTo("36200-000");

        assertThat(person.getAddresses().get(0).getLogradouro())
            .isEqualTo("Rua A");

        assertThat(person.getAddresses().get(0).getNumero())
            .isEqualTo("100");

        assertThat(person.getAddresses().get(0).getBairro())
            .isEqualTo("Centro");

        assertThat(person.getAddresses().get(0).getCidade())
            .isEqualTo("Barbacena");

        assertThat(person.getAddresses().get(0).getUf())
            .isEqualTo("MG");

        assertThat(person.getAddresses().get(0).isPrincipal())
            .isTrue();

        assertThat(person.getAddresses().get(0).getPerson())
            .isSameAs(person);

        verify(clientRepository)
            .existsByPersonCpfCnpj(
                "12345678900"
            );

        verify(personService)
            .getOrCreateForRegistration(
                "12345678900"
            );

        verify(personService)
            .updateFromDto(
                person,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );
    }

    @Test
    @DisplayName("Não deve criar cliente quando CPF/CNPJ já estiver cadastrado")
    void naoDeveCriarClienteQuandoCpfCnpjJaExiste() {

        ClientRequestDto dto =
            buildClientRequestDto(
                "123.456.789-00"
            );

        when(
            clientRepository.existsByPersonCpfCnpj(
                "12345678900"
            )
        ).thenReturn(true);

        assertThatThrownBy(() ->
            service.create(dto)
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "Esta pessoa/empresa já está cadastrada como cliente ativo."
            );

        verify(clientRepository)
            .existsByPersonCpfCnpj(
                "12345678900"
            );

        verify(clientRepository, never())
            .save(any(Client.class));

        verify(
            personService,
            never()
        ).getOrCreateForRegistration(any());
    }

    @Test
    @DisplayName("Não deve criar cliente quando já existir Person inativa com o CPF/CNPJ")
    void naoDeveCriarClienteQuandoJaExistirPersonInativa() {

        ClientRequestDto dto =
            buildClientRequestDto(
                "123.456.789-00"
            );

        when(
            clientRepository.existsByPersonCpfCnpj(
                "12345678900"
            )
        ).thenReturn(false);

        when(
            personService.getOrCreateForRegistration(
                "12345678900"
            )
        )
            .thenThrow(
                new BusinessException(
                    "Já existe um cadastro inativado para este CPF/CNPJ. " +
                    "Solicite a reativação ao Suporte."
                )
            );

        assertThatThrownBy(() ->
            service.create(dto)
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "Já existe um cadastro inativado para este CPF/CNPJ. " +
                "Solicite a reativação ao Suporte."
            );

        verify(clientRepository)
            .existsByPersonCpfCnpj(
                "12345678900"
            );

        verify(personService)
            .getOrCreateForRegistration(
                "12345678900"
            );

        verify(
            clientRepository,
            never()
        ).save(any(Client.class));
    }

    @Test
    @DisplayName("Deve retornar cliente quando o ID existir")
    void deveRetornarClienteQuandoIdExistir() {

        Person person =
            buildPerson(
                10L,
                "12345678900"
            );

        Client client =
            buildClient(
                1L,
                person
            );

        when(
            clientRepository.findByIdWithPerson(1L)
        )
            .thenReturn(
                Optional.of(client)
            );

        ClientResponseDto result =
            service.findById(1L);

        assertThat(result)
            .isNotNull();

        assertThat(result.id())
            .isEqualTo(1L);

        assertThat(result.person().id())
            .isEqualTo(10L);

        assertThat(result.person().tipoPessoa())
            .isEqualTo("PF");

        assertThat(result.person().name())
            .isEqualTo("João da Silva");

        assertThat(result.person().cpfCnpj())
            .isEqualTo("12345678900");

        assertThat(result.active())
            .isTrue();

        verify(clientRepository)
            .findByIdWithPerson(1L);
    }

    @Test
    @DisplayName("Deve lançar exceção quando o cliente não existir")
    void deveLancarExcecaoQuandoClienteNaoExistir() {

        when(
            clientRepository.findByIdWithPerson(999L)
        )
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.findById(999L)
        )
            .isInstanceOf(
                EntityNotFoundException.class
            )
            .hasMessage(
                "Cliente não encontrado com o ID: 999"
            );

        verify(clientRepository)
            .findByIdWithPerson(999L);
    }

    @Test
    @DisplayName("Deve atualizar cliente quando os dados forem válidos")
    void deveAtualizarClienteQuandoDadosForemValidos() {

        Person person =
            buildPerson(
                10L,
                "12345678900"
            );

        Client client =
            buildClient(
                1L,
                person
            );

        ClientRequestDto dto =
            buildClientRequestDto(
                "987.654.321-00"
            );

        when(
            clientRepository.findByIdWithPerson(1L)
        )
            .thenReturn(
                Optional.of(client)
            );

        doNothing()
            .when(personService)
            .updateFromDto(
                person,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );

        when(
            clientRepository.save(
                any(Client.class)
            )
        )
            .thenAnswer(
                invocation ->
                    invocation.getArgument(0)
            );

        ClientResponseDto result =
            service.update(
                1L,
                dto
            );

        assertThat(result)
            .isNotNull();

        assertThat(result.id())
            .isEqualTo(1L);

        verify(clientRepository)
            .findByIdWithPerson(1L);

        verify(personService)
            .updateFromDto(
                person,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );

        verify(clientRepository)
            .save(client);
    }

    @Test
    @DisplayName("Não deve atualizar quando CPF/CNPJ pertencer a outra pessoa")
    void naoDeveAtualizarQuandoCpfCnpjPertencerAOutraPessoa() {

        Person personCurrent =
            buildPerson(
                10L,
                "12345678900"
            );

        Client client =
            buildClient(
                1L,
                personCurrent
            );

        ClientRequestDto dto =
            buildClientRequestDto(
                "987.654.321-00"
            );

        when(
            clientRepository.findByIdWithPerson(1L)
        )
            .thenReturn(
                Optional.of(client)
            );

        doThrow(
            new BusinessException(
                "CPF/CNPJ já cadastrado para outra pessoa no sistema."
            )
        )
            .when(personService)
            .updateFromDto(
                eq(personCurrent),
                any(PersonRequestDto.class),
                any(IndividualPersonRequestDto.class),
                isNull(LegalEntityRequestDto.class)
            );

        assertThatThrownBy(() ->
            service.update(
                1L,
                dto
            )
        )
            .isInstanceOf(
                BusinessException.class
            )
            .hasMessage(
                "CPF/CNPJ já cadastrado para outra pessoa no sistema."
            );

        verify(clientRepository)
            .findByIdWithPerson(1L);

        verify(personService)
            .updateFromDto(
                eq(personCurrent),
                any(PersonRequestDto.class),
                any(IndividualPersonRequestDto.class),
                isNull(LegalEntityRequestDto.class)
            );

        verify(
            clientRepository,
            never()
        ).save(any(Client.class));
    }

    @Test
    @DisplayName("Deve permitir atualizar mantendo seu próprio CPF/CNPJ")
    void devePermitirAtualizarMantendoProprioCpfCnpj() {

        Person person =
            buildPerson(
                10L,
                "12345678900"
            );

        Client client =
            buildClient(
                1L,
                person
            );

        ClientRequestDto dto =
            buildClientRequestDto(
                "123.456.789-00"
            );

        when(
            clientRepository.findByIdWithPerson(1L)
        )
            .thenReturn(
                Optional.of(client)
            );

        doNothing()
            .when(personService)
            .updateFromDto(
                eq(person),
                eq(dto.person()),
                eq(dto.individual()),
                eq(dto.legalEntity())
            );

        when(
            clientRepository.save(
                any(Client.class)
            )
        )
            .thenAnswer(
                invocation ->
                    invocation.getArgument(0)
            );

        ClientResponseDto result =
            service.update(
                1L,
                dto
            );

        assertThat(result)
            .isNotNull();

        assertThat(result.id())
            .isEqualTo(1L);

        verify(clientRepository)
            .findByIdWithPerson(1L);

        verify(personService)
            .updateFromDto(
                person,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );

        verify(clientRepository)
            .save(client);
    }

    @Test
    @DisplayName("Deve lançar exceção ao atualizar cliente inexistente")
    void deveLancarExcecaoAoAtualizarClienteInexistente() {

        ClientRequestDto dto =
            buildClientRequestDto(
                "123.456.789-00"
            );

        when(
            clientRepository.findByIdWithPerson(999L)
        )
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.update(
                999L,
                dto
            )
        )
            .isInstanceOf(
                EntityNotFoundException.class
            )
            .hasMessage(
                "Cliente não encontrado com o ID: 999"
            );

        verify(clientRepository)
            .findByIdWithPerson(999L);

        verify(
            personService,
            never()
        ).updateFromDto(
            any(Person.class),
            any(PersonRequestDto.class),
            any(IndividualPersonRequestDto.class),
            any(LegalEntityRequestDto.class)
        );

        verify(
            clientRepository,
            never()
        ).save(any(Client.class));
    }

    @Test
    @DisplayName("Deve realizar soft delete do cliente")
    void deveRealizarSoftDeleteDoCliente() {

        Person person =
            buildPerson(
                10L,
                "12345678900"
            );

        Client client =
            buildClient(
                1L,
                person
            );

        when(
            clientRepository.findByIdWithPerson(1L)
        )
            .thenReturn(
                Optional.of(client)
            );

        service.delete(1L);

        assertThat(client.isActive())
            .isFalse();

        // A Person continua ativa.
        assertThat(person.isActive())
            .isTrue();

        verify(clientRepository)
            .findByIdWithPerson(1L);

        verify(clientRepository)
            .save(client);
    }

    @Test
    @DisplayName("Deve lançar exceção ao excluir cliente inexistente")
    void deveLancarExcecaoAoExcluirClienteInexistente() {

        when(
            clientRepository.findByIdWithPerson(999L)
        )
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.delete(999L)
        )
            .isInstanceOf(
                EntityNotFoundException.class
            )
            .hasMessage(
                "Cliente não encontrado com o ID: 999"
            );

        verify(clientRepository)
            .findByIdWithPerson(999L);

        verify(
            clientRepository,
            never()
        ).save(any(Client.class));
    }

    @Test
    @DisplayName("Deve retornar clientes paginados")
    void deveRetornarClientesPaginados() {

        Person person1 =
            buildPerson(
                10L,
                "12345678900"
            );

        Client client1 =
            buildClient(
                1L,
                person1
            );

        Person person2 =
            buildPerson(
                20L,
                "12345678000199"
            );

        person2.setTipoPessoa(
            TipoPessoa.PJ
        );

        Client client2 =
            buildClient(
                2L,
                person2
            );

        Pageable pageable =
            PageRequest.of(0, 10);

        Page<Client> clientPage =
            new PageImpl<>(
                List.of(
                    client1,
                    client2
                ),
                pageable,
                2
            );

        when(
            clientRepository.findAllWithPerson(
                pageable
            )
        )
            .thenReturn(clientPage);

        Page<ClientResponseDto> result =
            service.findAll(pageable);

        assertThat(result)
            .isNotNull();

        assertThat(result.getContent())
            .hasSize(2);

        assertThat(
            result.getContent()
                .get(0)
                .id()
        )
            .isEqualTo(1L);

        assertThat(
            result.getContent()
                .get(1)
                .id()
        )
            .isEqualTo(2L);

        assertThat(
            result.getContent()
                .get(0)
                .person()
                .tipoPessoa()
        )
            .isEqualTo("PF");

        assertThat(
            result.getContent()
                .get(1)
                .person()
                .tipoPessoa()
        )
            .isEqualTo("PJ");

        assertThat(
            result.getContent()
                .get(0)
                .person()
                .name()
        )
            .isEqualTo("João da Silva");

        assertThat(
            result.getContent()
                .get(1)
                .person()
                .cpfCnpj()
        )
            .isEqualTo("12345678000199");

        assertThat(result.getTotalElements())
            .isEqualTo(2);

        verify(clientRepository)
            .findAllWithPerson(pageable);
    }

    @Test
    @DisplayName("Deve retornar página vazia quando não houver clientes")
    void deveRetornarPaginaVaziaQuandoNaoHouverClientes() {

        Pageable pageable =
            PageRequest.of(0, 10);

        when(
            clientRepository.findAllWithPerson(
                pageable
            )
        )
            .thenReturn(
                Page.empty(pageable)
            );

        Page<ClientResponseDto> result =
            service.findAll(pageable);

        assertThat(result)
            .isNotNull();

        assertThat(result.getContent())
            .isEmpty();

        assertThat(result.getTotalElements())
            .isZero();

        verify(clientRepository)
            .findAllWithPerson(pageable);
    }

    @Test
    @DisplayName("Deve substituir os contatos ao atualizar cliente")
    void deveSubstituirContatosAoAtualizarCliente() {

        Person person =
            buildPerson(
                10L,
                "12345678900"
            );

        PersonContact oldContact =
            new PersonContact();

        oldContact.setType(ContactType.EMAIL);
        oldContact.setValue("antigo@email.com");
        oldContact.setPrincipal(true);

        person.addContact(oldContact);

        Client client =
            buildClient(
                1L,
                person
            );

        ClientRequestDto dto =
            buildClientRequestDto(
                "12345678900"
            );

        when(
            clientRepository.findByIdWithPerson(1L)
        )
            .thenReturn(Optional.of(client));

        doNothing()
            .when(personService)
            .updateFromDto(
                person,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );

        when(
            clientRepository.save(
                any(Client.class)
            )
        )
            .thenAnswer(
                invocation -> invocation.getArgument(0)
            );

        service.update(
            1L,
            dto
        );

        assertThat(person.getContacts())
            .hasSize(2);

        assertThat(
            person.getContacts()
                .stream()
                .map(PersonContact::getValue)
        )
            .containsExactly(
                "joao@email.com",
                "31999999999"
            );

        assertThat(person.getContacts())
            .allMatch(contact ->
                contact.getPerson() == person
            );
    }

    @Test
    @DisplayName("Deve manter contatos quando a lista não for informada na atualização")
    void deveManterContatosQuandoListaNaoForInformadaNaAtualizacao() {

        Person person =
            buildPerson(
                10L,
                "12345678900"
            );

        PersonContact contact =
            new PersonContact();

        contact.setType(ContactType.EMAIL);
        contact.setValue("joao@email.com");
        contact.setPrincipal(true);

        person.addContact(contact);

        Client client =
            buildClient(
                1L,
                person
            );

        ClientRequestDto dto =
            new ClientRequestDto(
                new PersonRequestDto(
                    "12345678900",
                    "PF",
                    "João Atualizado"
                ),
                new IndividualPersonRequestDto(
                    "495493478"
                ),
                null,
                null,
                null,
                true
            );

        when(
            clientRepository.findByIdWithPerson(1L)
        )
            .thenReturn(Optional.of(client));

        doNothing()
            .when(personService)
            .updateFromDto(
                person,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );

        when(
            clientRepository.save(
                any(Client.class)
            )
        )
            .thenAnswer(
                invocation -> invocation.getArgument(0)
            );

        service.update(
            1L,
            dto
        );

        assertThat(person.getContacts())
            .hasSize(1);

        assertThat(person.getContacts().get(0).getValue())
            .isEqualTo("joao@email.com");

        assertThat(person.getContacts().get(0).isPrincipal())
            .isTrue();
    }

    @Test
    @DisplayName("Deve remover todos os contatos quando a lista estiver vazia")
    void deveRemoverTodosOsContatosQuandoListaEstiverVazia() {

        Person person =
            buildPerson(
                10L,
                "12345678900"
            );

        PersonContact contact =
            new PersonContact();

        contact.setType(ContactType.EMAIL);
        contact.setValue("joao@email.com");
        contact.setPrincipal(true);

        person.addContact(contact);

        Client client =
            buildClient(
                1L,
                person
            );

        ClientRequestDto dto =
            new ClientRequestDto(
                new PersonRequestDto(
                    "12345678900",
                    "PF",
                    "João da Silva"
                ),
                new IndividualPersonRequestDto(
                    "495493478"
                ),
                null,
                List.of(),
                null,
                true
            );

        when(
            clientRepository.findByIdWithPerson(1L)
        )
            .thenReturn(Optional.of(client));

        doNothing()
            .when(personService)
            .updateFromDto(
                person,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );

        when(
            clientRepository.save(
                any(Client.class)
            )
        )
            .thenAnswer(
                invocation -> invocation.getArgument(0)
            );

        service.update(
            1L,
            dto
        );

        assertThat(person.getContacts())
            .isEmpty();
    }

    @Test
    @DisplayName("Deve rejeitar tipo de contato inválido")
    void deveRejeitarTipoDeContatoInvalido() {

        Person person =
            buildPerson(
                10L,
                "12345678900"
            );

        Client client =
            buildClient(
                1L,
                person
            );

        ClientRequestDto dto =
            new ClientRequestDto(
                new PersonRequestDto(
                    "12345678900",
                    "PF",
                    "João da Silva"
                ),
                new IndividualPersonRequestDto(
                    "495493478"
                ),
                null,
                List.of(
                    new PersonContactRequestDto(
                        "FAX",
                        "31999999999",
                        false,
                        null
                    )
                ),
                null,
                true
            );

        when(
            clientRepository.findByIdWithPerson(1L)
        )
            .thenReturn(Optional.of(client));

        doNothing()
            .when(personService)
            .updateFromDto(
                person,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );

        assertThatThrownBy(() ->
            service.update(1L, dto)
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "Tipo de contato inválido: FAX"
            );

        verify(
            clientRepository,
            never()
        ).save(any(Client.class));
    }

    @Test
    @DisplayName("Deve substituir os endereços ao atualizar cliente")
    void deveSubstituirEnderecosAoAtualizarCliente() {

        Person person =
            buildPerson(
                10L,
                "12345678900"
            );

        PersonAddress oldAddress =
            new PersonAddress();

        oldAddress.setType(
            AddressType.RESIDENCIAL
        );

        oldAddress.setCep(
            "36200000"
        );

        oldAddress.setLogradouro(
            "Rua Antiga"
        );

        oldAddress.setNumero(
            "10"
        );

        person.addAddress(oldAddress);

        Client client =
            buildClient(
                1L,
                person
            );

        ClientRequestDto dto =
            new ClientRequestDto(
                new PersonRequestDto(
                    "12345678900",
                    "PF",
                    "João da Silva"
                ),
                new IndividualPersonRequestDto(
                    "495493478"
                ),
                null,
                null,
                List.of(
                    new PersonAddressRequestDto(
                        "COMERCIAL",
                        "30130-010",
                        "Avenida Nova",
                        "500",
                        "Sala 10",
                        "Centro",
                        "Belo Horizonte",
                        "MG",
                        true
                    )
                ),
                true
            );

        when(
            clientRepository.findByIdWithPerson(1L)
        )
            .thenReturn(
                Optional.of(client)
            );

        doNothing()
            .when(personService)
            .updateFromDto(
                person,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );

        when(
            clientRepository.save(
                any(Client.class)
            )
        )
            .thenAnswer(
                invocation -> invocation.getArgument(0)
            );

        service.update(
            1L,
            dto
        );

        assertThat(
            person.getAddresses()
        )
            .hasSize(1);

        PersonAddress address =
            person.getAddresses().get(0);

        assertThat(address.getType())
            .isEqualTo(
                AddressType.COMERCIAL
            );

        assertThat(address.getCep())
            .isEqualTo(
                "30130-010"
            );

        assertThat(address.getLogradouro())
            .isEqualTo(
                "Avenida Nova"
            );

        assertThat(address.getNumero())
            .isEqualTo(
                "500"
            );

        assertThat(address.getComplemento())
            .isEqualTo(
                "Sala 10"
            );

        assertThat(address.getBairro())
            .isEqualTo(
                "Centro"
            );

        assertThat(address.getCidade())
            .isEqualTo(
                "Belo Horizonte"
            );

        assertThat(address.getUf())
            .isEqualTo(
                "MG"
            );

        assertThat(address.isPrincipal())
            .isTrue();

        assertThat(address.getPerson())
            .isSameAs(person);
    }


    @Test
    @DisplayName("Deve manter endereços quando a lista não for informada na atualização")
    void deveManterEnderecosQuandoListaNaoForInformadaNaAtualizacao() {

        Person person =
            buildPerson(
                10L,
                "12345678900"
            );

        PersonAddress address =
            new PersonAddress();

        address.setType(
            AddressType.RESIDENCIAL
        );

        address.setCep(
            "36200000"
        );

        address.setLogradouro(
            "Rua A"
        );

        address.setNumero(
            "100"
        );

        address.setCidade(
            "Barbacena"
        );

        address.setUf(
            "MG"
        );

        person.addAddress(address);

        Client client =
            buildClient(
                1L,
                person
            );

        ClientRequestDto dto =
            new ClientRequestDto(
                new PersonRequestDto(
                    "12345678900",
                    "PF",
                    "João Atualizado"
                ),
                new IndividualPersonRequestDto(
                    "495493478"
                ),
                null,
                null,
                null,
                true
            );

        when(
            clientRepository.findByIdWithPerson(1L)
        )
            .thenReturn(
                Optional.of(client)
            );

        doNothing()
            .when(personService)
            .updateFromDto(
                person,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );

        when(
            clientRepository.save(
                any(Client.class)
            )
        )
            .thenAnswer(
                invocation -> invocation.getArgument(0)
            );

        service.update(
            1L,
            dto
        );

        assertThat(
            person.getAddresses()
        )
            .hasSize(1);

        PersonAddress preservedAddress =
            person.getAddresses().get(0);

        assertThat(
            preservedAddress.getType()
        )
            .isEqualTo(
                AddressType.RESIDENCIAL
            );

        assertThat(
            preservedAddress.getCep()
        )
            .isEqualTo(
                "36200000"
            );

        assertThat(
            preservedAddress.getLogradouro()
        )
            .isEqualTo(
                "Rua A"
            );

        assertThat(
            preservedAddress.getNumero()
        )
            .isEqualTo(
                "100"
            );

        assertThat(
            preservedAddress.getPerson()
        )
            .isSameAs(person);
    }


    @Test
    @DisplayName("Deve remover todos os endereços quando a lista estiver vazia")
    void deveRemoverTodosOsEnderecosQuandoListaEstiverVazia() {

        Person person =
            buildPerson(
                10L,
                "12345678900"
            );

        PersonAddress address =
            new PersonAddress();

        address.setType(
            AddressType.RESIDENCIAL
        );

        address.setCep(
            "36200000"
        );

        address.setLogradouro(
            "Rua A"
        );

        address.setNumero(
            "100"
        );

        person.addAddress(address);

        Client client =
            buildClient(
                1L,
                person
            );

        ClientRequestDto dto =
            new ClientRequestDto(
                new PersonRequestDto(
                    "12345678900",
                    "PF",
                    "João da Silva"
                ),
                new IndividualPersonRequestDto(
                    "495493478"
                ),
                null,
                null,
                List.of(),
                true
            );

        when(
            clientRepository.findByIdWithPerson(1L)
        )
            .thenReturn(
                Optional.of(client)
            );

        doNothing()
            .when(personService)
            .updateFromDto(
                person,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );

        when(
            clientRepository.save(
                any(Client.class)
            )
        )
            .thenAnswer(
                invocation -> invocation.getArgument(0)
            );

        service.update(
            1L,
            dto
        );

        assertThat(
            person.getAddresses()
        )
            .isEmpty();
    }


    @Test
    @DisplayName("Deve rejeitar tipo de endereço inválido")
    void deveRejeitarTipoDeEnderecoInvalido() {

        Person person =
            buildPerson(
                10L,
                "12345678900"
            );

        Client client =
            buildClient(
                1L,
                person
            );

        ClientRequestDto dto =
            new ClientRequestDto(
                new PersonRequestDto(
                    "12345678900",
                    "PF",
                    "João da Silva"
                ),
                new IndividualPersonRequestDto(
                    "495493478"
                ),
                null,
                null,
                List.of(
                    new PersonAddressRequestDto(
                        "INVALIDO",
                        "36200000",
                        "Rua A",
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

        when(
            clientRepository.findByIdWithPerson(1L)
        )
            .thenReturn(
                Optional.of(client)
            );

        doNothing()
            .when(personService)
            .updateFromDto(
                person,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );

        assertThatThrownBy(() ->
            service.update(
                1L,
                dto
            )
        )
            .isInstanceOf(
                BusinessException.class
            )
            .hasMessage(
                "Tipo de endereço inválido: INVALIDO"
            );

        verify(
            clientRepository,
            never()
        )
            .save(
                any(Client.class)
            );
    }

}