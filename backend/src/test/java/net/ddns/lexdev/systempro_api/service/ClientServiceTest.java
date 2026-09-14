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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.domain.Client;
import net.ddns.lexdev.systempro_api.dto.ClientRequestDto;
import net.ddns.lexdev.systempro_api.dto.ClientResponseDto;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.ClientRepository;



@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository repository;

    @InjectMocks
    private ClientService service;

    @Test
    @DisplayName("Deve criar cliente com CPF/CNPJ sanitizado")
    void deveCriarClienteComCpfCnpjSanitizado() {

        ClientRequestDto dto = new ClientRequestDto(
                "FISICA",
                "João da Silva",
                null,
                "123.456.789-00",
                null,
                "joao@email.com",
                "31999999999",
                "36200-000",
                "Rua A",
                "100",
                null,
                "Centro",
                "Barbacena",
                "MG",
                true
        );

        when(repository.existsByCpfCnpj("12345678900"))
                .thenReturn(false);

        Client clientSalvo = new Client();
        clientSalvo.setId(1L);
        clientSalvo.setTipoPessoa("FISICA");
        clientSalvo.setName("João da Silva");
        clientSalvo.setCpfCnpj("12345678900");
        clientSalvo.setActive(true);

        when(repository.save(any(Client.class)))
                .thenReturn(clientSalvo);

        ClientResponseDto result = service.create(dto);

        ArgumentCaptor<Client> clientCaptor =
                ArgumentCaptor.forClass(Client.class);

        verify(repository).save(clientCaptor.capture());

        Client clientEnviadoParaRepository = clientCaptor.getValue();

        assertThat(clientEnviadoParaRepository.getCpfCnpj())
                .isEqualTo("12345678900");

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("João da Silva");

        verify(repository).existsByCpfCnpj("12345678900");
    }

    @Test
    @DisplayName("Não deve criar cliente quando CPF/CNPJ já estiver cadastrado")
    void naoDeveCriarClienteQuandoCpfCnpjJaExiste() {

        ClientRequestDto dto = new ClientRequestDto(
                "FISICA",
                "João da Silva",
                null,
                "123.456.789-00",
                null,
                "joao@email.com",
                "31999999999",
                "36200-000",
                "Rua A",
                "100",
                null,
                "Centro",
                "Barbacena",
                "MG",
                true
        );

        when(repository.existsByCpfCnpj("12345678900"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Já existe um cliente ativo cadastrado com este CPF/CNPJ.");

        verify(repository).existsByCpfCnpj("12345678900");

        verify(repository, never())
                .save(any(Client.class));
    }

    @Test 
    @DisplayName("Deve retornar cliente quando o ID existir") 
    void deveRetornarClienteQuandoIdExistir() { 
        Client client = new Client(); 
        client.setId(1L); 
        client.setTipoPessoa("FISICA"); 
        client.setName("João da Silva"); 
        client.setCpfCnpj("12345678900"); 
        client.setEmail("joao@email.com"); 
        client.setPhone("31999999999"); 
        client.setActive(true); 
        when(repository.findById(1L)).thenReturn(Optional.of(client)); 
        ClientResponseDto result = service.findById(1L); assertThat(result).isNotNull(); 
        assertThat(result.id()).isEqualTo(1L); 
        assertThat(result.tipoPessoa()).isEqualTo("FISICA"); 
        assertThat(result.name()).isEqualTo("João da Silva"); 
        assertThat(result.cpfCnpj()).isEqualTo("12345678900"); 
        assertThat(result.email()).isEqualTo("joao@email.com"); 
        assertThat(result.phone()).isEqualTo("31999999999"); 
        assertThat(result.active()).isTrue(); verify(repository).findById(1L); 
    } 
    
    @Test 
    @DisplayName("Deve lançar exceção quando o cliente não existir") 
    void deveLancarExcecaoQuandoClienteNaoExistir() { 
        when(repository.findById(999L)) .thenReturn(Optional.empty()); 
        assertThatThrownBy(() -> service.findById(999L)) .isInstanceOf(EntityNotFoundException.class) .hasMessage("Cliente não encontrado com o ID: 999"); 
        verify(repository).findById(999L); 
    }


    @Test
    @DisplayName("Deve atualizar cliente quando os dados forem válidos")
    void deveAtualizarClienteQuandoDadosForemValidos() {

        Client client = new Client();

        client.setId(1L);
        client.setTipoPessoa("FISICA");
        client.setName("João da Silva");
        client.setCpfCnpj("12345678900");
        client.setEmail("joao@email.com");
        client.setPhone("31999999999");
        client.setActive(true);

        ClientRequestDto dto = new ClientRequestDto(
                "FISICA",
                "João da Silva Atualizado",
                "João da Silva ME",
                "987.654.321-00",
                "MG123456",
                "joao.atualizado@email.com",
                "31988888888",
                "36200-000",
                "Rua Nova",
                "200",
                "Apt. 3",
                "Centro",
                "Barbacena",
                "MG",
                true
        );

        when(repository.findById(1L))
                .thenReturn(Optional.of(client));

        when(repository.findByCpfCnpj("98765432100"))
                .thenReturn(Optional.empty());

        when(repository.save(any(Client.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ClientResponseDto result = service.update(1L, dto);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("João da Silva Atualizado");
        assertThat(result.nomeFantasia()).isEqualTo("João da Silva ME");
        assertThat(result.cpfCnpj()).isEqualTo("98765432100");
        assertThat(result.email()).isEqualTo("joao.atualizado@email.com");
        assertThat(result.phone()).isEqualTo("31988888888");
        assertThat(result.cep()).isEqualTo("36200-000");
        assertThat(result.logradouro()).isEqualTo("Rua Nova");
        assertThat(result.numero()).isEqualTo("200");
        assertThat(result.complemento()).isEqualTo("Apt. 3");
        assertThat(result.bairro()).isEqualTo("Centro");
        assertThat(result.cidade()).isEqualTo("Barbacena");
        assertThat(result.uf()).isEqualTo("MG");
        assertThat(result.active()).isTrue();

        verify(repository).findById(1L);
        verify(repository).findByCpfCnpj("98765432100");
        verify(repository).save(client);
    }


    @Test
    @DisplayName("Não deve atualizar quando CPF/CNPJ pertencer a outro cliente")
    void naoDeveAtualizarQuandoCpfCnpjPertencerAOutroCliente() {

        Client client = new Client();

        client.setId(1L);
        client.setName("João da Silva");
        client.setCpfCnpj("12345678900");
        client.setActive(true);

        Client existingClient = new Client();

        existingClient.setId(2L);
        existingClient.setName("Maria da Silva");
        existingClient.setCpfCnpj("98765432100");
        existingClient.setActive(true);

        ClientRequestDto dto = new ClientRequestDto(
                "FISICA",
                "João da Silva Atualizado",
                null,
                "987.654.321-00",
                null,
                "joao@email.com",
                "31999999999",
                "36200-000",
                "Rua A",
                "100",
                null,
                "Centro",
                "Barbacena",
                "MG",
                true
        );

        when(repository.findById(1L))
                .thenReturn(Optional.of(client));

        when(repository.findByCpfCnpj("98765432100"))
                .thenReturn(Optional.of(existingClient));

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CPF/CNPJ já cadastrado para outro cliente.");

        verify(repository).findById(1L);
        verify(repository).findByCpfCnpj("98765432100");

        verify(repository, never())
                .save(any(Client.class));
    }

    @Test
    @DisplayName("Deve permitir atualizar o cliente mantendo seu próprio CPF/CNPJ")
    void devePermitirAtualizarMantendoProprioCpfCnpj() {

        Client client = new Client();

        client.setId(1L);
        client.setTipoPessoa("FISICA");
        client.setName("João da Silva");
        client.setCpfCnpj("12345678900");
        client.setEmail("joao@email.com");
        client.setPhone("31999999999");
        client.setActive(true);

        ClientRequestDto dto = new ClientRequestDto(
                "FISICA",
                "João da Silva Atualizado",
                null,
                "123.456.789-00",
                null,
                "joao.novo@email.com",
                "31988888888",
                "36200-000",
                "Rua Nova",
                "200",
                null,
                "Centro",
                "Barbacena",
                "MG",
                true
        );

        when(repository.findById(1L))
                .thenReturn(Optional.of(client));

        when(repository.findByCpfCnpj("12345678900"))
                .thenReturn(Optional.of(client));

        when(repository.save(any(Client.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ClientResponseDto result = service.update(1L, dto);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("João da Silva Atualizado");
        assertThat(result.cpfCnpj()).isEqualTo("12345678900");
        assertThat(result.email()).isEqualTo("joao.novo@email.com");
        assertThat(result.phone()).isEqualTo("31988888888");
        assertThat(result.active()).isTrue();

        verify(repository).findById(1L);
        verify(repository).findByCpfCnpj("12345678900");
        verify(repository).save(client);
    }

    @Test
    @DisplayName("Deve lançar exceção ao atualizar cliente inexistente")
    void deveLancarExcecaoAoAtualizarClienteInexistente() {

        ClientRequestDto dto = new ClientRequestDto(
                "FISICA",
                "João da Silva",
                null,
                "123.456.789-00",
                null,
                "joao@email.com",
                "31999999999",
                "36200-000",
                "Rua A",
                "100",
                null,
                "Centro",
                "Barbacena",
                "MG",
                true
        );

        when(repository.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Cliente não encontrado com o ID: 999");

        verify(repository).findById(999L);

        verify(repository, never())
                .findByCpfCnpj(any());

        verify(repository, never())
                .save(any(Client.class));
    }


    @Test
    @DisplayName("Deve realizar soft delete do cliente")
    void deveRealizarSoftDeleteDoCliente() {

        Client client = new Client();

        client.setId(1L);
        client.setName("João da Silva");
        client.setCpfCnpj("12345678900");
        client.setActive(true);

        when(repository.findById(1L))
                .thenReturn(Optional.of(client));

        service.delete(1L);

        assertThat(client.getActive()).isFalse();

        verify(repository).findById(1L);
        verify(repository).save(client);
    }


    @Test
    @DisplayName("Deve lançar exceção ao excluir cliente inexistente")
    void deveLancarExcecaoAoExcluirClienteInexistente() {

        when(repository.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Cliente não encontrado com o ID: 999");

        verify(repository).findById(999L);

        verify(repository, never())
                .save(any(Client.class));
    }


    @Test
    @DisplayName("Deve retornar clientes paginados")
    void deveRetornarClientesPaginados() {

        Client client1 = new Client();

        client1.setId(1L);
        client1.setTipoPessoa("FISICA");
        client1.setName("João da Silva");
        client1.setCpfCnpj("12345678900");
        client1.setEmail("joao@email.com");
        client1.setPhone("31999999999");
        client1.setActive(true);

        Client client2 = new Client();

        client2.setId(2L);
        client2.setTipoPessoa("JURIDICA");
        client2.setName("Empresa XYZ");
        client2.setCpfCnpj("12345678000199");
        client2.setEmail("empresa@email.com");
        client2.setPhone("3133333333");
        client2.setActive(true);

        Pageable pageable = PageRequest.of(0, 10);

        Page<Client> clientPage =
                new PageImpl<>(
                        List.of(client1, client2),
                        pageable,
                        2
                );

        when(repository.findAll(pageable))
                .thenReturn(clientPage);

        Page<ClientResponseDto> result =
                service.findAll(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);

        assertThat(result.getContent().get(0).id())
                .isEqualTo(1L);

        assertThat(result.getContent().get(0).name())
                .isEqualTo("João da Silva");

        assertThat(result.getContent().get(0).cpfCnpj())
                .isEqualTo("12345678900");

        assertThat(result.getContent().get(1).id())
                .isEqualTo(2L);

        assertThat(result.getContent().get(1).name())
                .isEqualTo("Empresa XYZ");

        assertThat(result.getContent().get(1).cpfCnpj())
                .isEqualTo("12345678000199");

        assertThat(result.getTotalElements())
                .isEqualTo(2);

        assertThat(result.getSize())
                .isEqualTo(10);

        verify(repository).findAll(pageable);
    }

    @Test
    @DisplayName("Deve manter cliente inativo ao realizar soft delete novamente")
    void deveManterClienteInativoAoRealizarSoftDeleteNovamente() {

        Client client = new Client();

        client.setId(1L);
        client.setName("João da Silva");
        client.setCpfCnpj("12345678900");
        client.setActive(false);

        when(repository.findById(1L))
                .thenReturn(Optional.of(client));

        service.delete(1L);

        assertThat(client.getActive()).isFalse();

        verify(repository).findById(1L);
        verify(repository).save(client);
    }

    @Test
    @DisplayName("Deve retornar página vazia quando não houver clientes")
    void deveRetornarPaginaVaziaQuandoNaoHouverClientes() {

        Pageable pageable = PageRequest.of(0, 10);

        when(repository.findAll(pageable))
                .thenReturn(Page.empty(pageable));

        Page<ClientResponseDto> result =
                service.findAll(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getSize()).isEqualTo(10);

        verify(repository).findAll(pageable);
    }



}
