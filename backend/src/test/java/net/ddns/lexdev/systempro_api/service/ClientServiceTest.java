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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doCallRealMethod;
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
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.dto.ClientRequestDto;
import net.ddns.lexdev.systempro_api.dto.ClientResponseDto;
import net.ddns.lexdev.systempro_api.dto.PersonRequestDto;
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
        PersonRequestDto personDto = new PersonRequestDto(
                cpfCnpj,
                "PF",
                "João da Silva",
                null,
                null,
                "joao@email.com",
                "31999999999",
                "36200-000",
                "Rua A",
                "100",
                null,
                "Centro",
                "Barbacena",
                "MG"
        );

        return new ClientRequestDto(personDto, true);
    }

    private Person buildPerson(Long id, String cpfCnpj) {
        Person person = new Person();
        ReflectionTestUtils.setField(person, "id", id);
        person.setTipoPessoa(TipoPessoa.PF);
        person.setName("João da Silva");
        person.setCpfCnpj(cpfCnpj);
        person.setEmail("joao@email.com");
        person.setPhone("31999999999");
        person.setCep("36200-000");
        person.setLogradouro("Rua A");
        person.setNumero("100");
        person.setBairro("Centro");
        person.setCidade("Barbacena");
        person.setUf("MG");
        person.setActive(true);
        return person;
    }

    private Client buildClient(Long id, Person person) {
        Client client = new Client();
        ReflectionTestUtils.setField(client, "id", id);
        client.setPerson(person);
        client.setActive(true);
        return client;
    }

    @Test
    @DisplayName("Deve criar cliente com CPF/CNPJ sanitizado")
    void deveCriarClienteComCpfCnpjSanitizado() {
        ClientRequestDto dto = buildClientRequestDto("123.456.789-00");
        Person person = buildPerson(10L, "12345678900");
        Client clientSalvo = buildClient(1L, person);

        when(clientRepository.existsByPersonCpfCnpj("12345678900")).thenReturn(false);
        when(personService.getOrCreateForRegistration("12345678900")).thenReturn(person);
        doCallRealMethod().when(personService).copyDtoToPerson(any(), any());
        when(clientRepository.save(any(Client.class))).thenReturn(clientSalvo);

        ClientResponseDto result = service.create(dto);

        ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(clientCaptor.capture());

        Client clientEnviadoParaRepository = clientCaptor.getValue();
        assertThat(clientEnviadoParaRepository.getPerson().getCpfCnpj()).isEqualTo("12345678900");

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("João da Silva");

        verify(clientRepository).existsByPersonCpfCnpj("12345678900");
        verify(personService).getOrCreateForRegistration("12345678900");
        verify(personService).copyDtoToPerson(dto.person(), person);
    }

    @Test
    @DisplayName("Não deve criar cliente quando CPF/CNPJ já estiver cadastrado")
    void naoDeveCriarClienteQuandoCpfCnpjJaExiste() {
        ClientRequestDto dto = buildClientRequestDto("123.456.789-00");

        when(clientRepository.existsByPersonCpfCnpj("12345678900")).thenReturn(true);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Esta pessoa/empresa já está cadastrada como cliente ativo.");

        verify(clientRepository).existsByPersonCpfCnpj("12345678900");
        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    @DisplayName("Não deve criar cliente quando já existir Person inativa com o CPF/CNPJ")
    void naoDeveCriarClienteQuandoJaExistirPersonInativa() {
        ClientRequestDto dto = buildClientRequestDto("123.456.789-00");

        when(clientRepository.existsByPersonCpfCnpj("12345678900")).thenReturn(false);
        when(personService.getOrCreateForRegistration("12345678900"))
                .thenThrow(new BusinessException(
                        "Já existe um cadastro inativado para este CPF/CNPJ. " +
                        "Solicite a reativação ao Suporte."
                ));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Já existe um cadastro inativado para este CPF/CNPJ. Solicite a reativação ao Suporte.");

        verify(clientRepository).existsByPersonCpfCnpj("12345678900");
        verify(personService).getOrCreateForRegistration("12345678900");
        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    @DisplayName("Deve retornar cliente quando o ID existir")
    void deveRetornarClienteQuandoIdExistir() {
        Person person = buildPerson(10L, "12345678900");
        Client client = buildClient(1L, person);

        when(clientRepository.findByIdWithPerson(1L)).thenReturn(Optional.of(client));

        ClientResponseDto result = service.findById(1L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.tipoPessoa()).isEqualTo("PF");
        assertThat(result.name()).isEqualTo("João da Silva");
        assertThat(result.cpfCnpj()).isEqualTo("12345678900");
        assertThat(result.email()).isEqualTo("joao@email.com");
        assertThat(result.phone()).isEqualTo("31999999999");

        verify(clientRepository).findByIdWithPerson(1L);
    }

    @Test
    @DisplayName("Deve lançar exceção quando o cliente não existir")
    void deveLancarExcecaoQuandoClienteNaoExistir() {
        when(clientRepository.findByIdWithPerson(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Cliente não encontrado com o ID: 999");

        verify(clientRepository).findByIdWithPerson(999L);
    }

    @Test
    @DisplayName("Deve atualizar cliente quando os dados forem válidos")
    void deveAtualizarClienteQuandoDadosForemValidos() {
        Person person = buildPerson(10L, "12345678900");
        Client client = buildClient(1L, person);

        PersonRequestDto personDto = new PersonRequestDto(
                "987.654.321-00",
                "PF",
                "João da Silva Atualizado",
                "João da Silva ME",
                "MG123456",
                "joao.atualizado@email.com",
                "31988888888",
                "36200-000",
                "Rua Nova",
                "200",
                "Apt. 3",
                "Centro",
                "Barbacena",
                "MG"
        );
        ClientRequestDto dto = new ClientRequestDto(personDto, true);

        when(clientRepository.findByIdWithPerson(1L)).thenReturn(Optional.of(client));
        doNothing().when(personService).ensureCpfCnpjAvailable("98765432100", 10L);
        doCallRealMethod().when(personService).copyDtoToPerson(any(), any());
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClientResponseDto result = service.update(1L, dto);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("João da Silva Atualizado");
        assertThat(result.nomeFantasia()).isEqualTo("João da Silva ME");
        assertThat(result.cpfCnpj()).isEqualTo("98765432100");
        assertThat(result.email()).isEqualTo("joao.atualizado@email.com");

        verify(clientRepository).findByIdWithPerson(1L);
        verify(personService).ensureCpfCnpjAvailable("98765432100", 10L);
        verify(personService).copyDtoToPerson(dto.person(), person);
        verify(clientRepository).save(client);
    }

    @Test
    @DisplayName("Não deve atualizar quando CPF/CNPJ pertencer a outra pessoa")
    void naoDeveAtualizarQuandoCpfCnpjPertencerAOutraPessoa() {
        Person personCurrent = buildPerson(10L, "12345678900");
        Client client = buildClient(1L, personCurrent);

        ClientRequestDto dto = buildClientRequestDto("987.654.321-00");

        when(clientRepository.findByIdWithPerson(1L)).thenReturn(Optional.of(client));
        doThrow(new BusinessException("CPF/CNPJ já cadastrado para outra pessoa no sistema."))
                .when(personService).ensureCpfCnpjAvailable("98765432100", 10L);

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CPF/CNPJ já cadastrado para outra pessoa no sistema.");

        verify(clientRepository).findByIdWithPerson(1L);
        verify(personService).ensureCpfCnpjAvailable("98765432100", 10L);
        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    @DisplayName("Deve permitir atualizar o cliente mantendo seu próprio CPF/CNPJ")
    void devePermitirAtualizarMantendoProprioCpfCnpj() {
        Person person = buildPerson(10L, "12345678900");
        Client client = buildClient(1L, person);
        ClientRequestDto dto = buildClientRequestDto("123.456.789-00");

        when(clientRepository.findByIdWithPerson(1L)).thenReturn(Optional.of(client));
        doNothing().when(personService).ensureCpfCnpjAvailable("12345678900", 10L);
        doCallRealMethod().when(personService).copyDtoToPerson(any(), any());
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClientResponseDto result = service.update(1L, dto);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.cpfCnpj()).isEqualTo("12345678900");

        verify(clientRepository).findByIdWithPerson(1L);
        verify(personService).ensureCpfCnpjAvailable("12345678900", 10L);
        verify(clientRepository).save(client);
    }

    @Test
    @DisplayName("Deve lançar exceção ao atualizar cliente inexistente")
    void deveLancarExcecaoAoAtualizarClienteInexistente() {
        ClientRequestDto dto = buildClientRequestDto("123.456.789-00");

        when(clientRepository.findByIdWithPerson(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Cliente não encontrado com o ID: 999");

        verify(clientRepository).findByIdWithPerson(999L);
        verify(personService, never()).ensureCpfCnpjAvailable(any(), any());
        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    @DisplayName("Deve realizar soft delete do cliente")
    void deveRealizarSoftDeleteDoCliente() {
        Person person = buildPerson(10L, "12345678900");
        Client client = buildClient(1L, person);

        when(clientRepository.findByIdWithPerson(1L)).thenReturn(Optional.of(client));

        service.delete(1L);

        assertThat(client.isActive()).isFalse();

        verify(clientRepository).findByIdWithPerson(1L);
        verify(clientRepository).save(client);
    }

    @Test
    @DisplayName("Deve lançar exceção ao excluir cliente inexistente")
    void deveLancarExcecaoAoExcluirClienteInexistente() {
        when(clientRepository.findByIdWithPerson(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Cliente não encontrado com o ID: 999");

        verify(clientRepository).findByIdWithPerson(999L);
        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    @DisplayName("Deve retornar clientes paginados")
    void deveRetornarClientesPaginados() {
        Person person1 = buildPerson(10L, "12345678900");
        Client client1 = buildClient(1L, person1);

        Person person2 = buildPerson(20L, "12345678000199");
        person2.setTipoPessoa(TipoPessoa.PJ);
        Client client2 = buildClient(2L, person2);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Client> clientPage = new PageImpl<>(List.of(client1, client2), pageable, 2);

        when(clientRepository.findAllWithPerson(pageable)).thenReturn(clientPage);

        Page<ClientResponseDto> result = service.findAll(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).id()).isEqualTo(1L);
        assertThat(result.getContent().get(1).id()).isEqualTo(2L);
        assertThat(result.getContent().get(0).tipoPessoa()).isEqualTo("PF");
        assertThat(result.getContent().get(1).tipoPessoa()).isEqualTo("PJ");
        assertThat(result.getTotalElements()).isEqualTo(2);

        verify(clientRepository).findAllWithPerson(pageable);
    }

    @Test
    @DisplayName("Deve retornar página vazia quando não houver clientes")
    void deveRetornarPaginaVaziaQuandoNaoHouverClientes() {
        Pageable pageable = PageRequest.of(0, 10);

        when(clientRepository.findAllWithPerson(pageable)).thenReturn(Page.empty(pageable));

        Page<ClientResponseDto> result = service.findAll(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();

        verify(clientRepository).findAllWithPerson(pageable);
    }
}