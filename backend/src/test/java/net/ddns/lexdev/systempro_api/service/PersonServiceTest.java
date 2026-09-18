package net.ddns.lexdev.systempro_api.service;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.dto.PersonRequestDto;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.mapper.IndividualPersonMapper;
import net.ddns.lexdev.systempro_api.mapper.LegalEntityMapper;
import net.ddns.lexdev.systempro_api.mapper.PersonMapper;
import net.ddns.lexdev.systempro_api.repository.PersonRepository;

@ExtendWith(MockitoExtension.class)
class PersonServiceTest {

    @Mock
    private PersonRepository personRepository;

    private PersonMapper personMapper;

    private PersonService service;
    private IndividualPersonMapper individualPersonMapper;
    private LegalEntityMapper legalEntityMapper;

    @BeforeEach
    void setUp() {

        personMapper = new PersonMapper();
        individualPersonMapper = new IndividualPersonMapper();
        legalEntityMapper = new LegalEntityMapper();

        service = new PersonService(
            personRepository,
            personMapper,
            individualPersonMapper,
            legalEntityMapper
        );
    }

    private Person buildPerson(
        Long id,
        String cpfCnpj,
        boolean active
    ) {
        Person person = new Person();

        ReflectionTestUtils.setField(person, "id", id);

        person.setCpfCnpj(cpfCnpj);
        person.setActive(active);

        return person;
    }

    @Test
    @DisplayName("Deve retornar Person ativa quando CPF/CNPJ já estiver cadastrado")
    void deveRetornarPersonAtivaQuandoCpfCnpjJaEstiverCadastrado() {

        Person person =
            buildPerson(1L, "12345678900", true);

        when(personRepository.findByCpfCnpj("12345678900"))
            .thenReturn(Optional.of(person));

        Person result =
            service.getOrCreateForRegistration("12345678900");

        assertThat(result)
            .isSameAs(person);

        assertThat(result.isActive())
            .isTrue();

        verify(personRepository)
            .findByCpfCnpj("12345678900");

        verify(personRepository, never())
            .findIncludingInactiveByCpfCnpj("12345678900");
    }

    @Test
    @DisplayName("Deve criar nova Person quando CPF/CNPJ não existir")
    void deveCriarNovaPersonQuandoCpfCnpjNaoExistir() {

        when(personRepository.findByCpfCnpj("12345678900"))
            .thenReturn(Optional.empty());

        when(personRepository.findIncludingInactiveByCpfCnpj("12345678900"))
            .thenReturn(Optional.empty());

        Person result =
            service.getOrCreateForRegistration("12345678900");

        assertThat(result)
            .isNotNull();

        assertThat(result.getId())
            .isNull();

        assertThat(result.getCpfCnpj())
            .isEqualTo("12345678900");

        assertThat(result.isActive())
            .isTrue();

        verify(personRepository)
            .findByCpfCnpj("12345678900");

        verify(personRepository)
            .findIncludingInactiveByCpfCnpj("12345678900");

        verify(personRepository, never())
            .save(any(Person.class));
    }

    @Test
    @DisplayName("Deve normalizar CPF/CNPJ ao criar nova Person")
    void deveNormalizarCpfCnpjAoCriarNovaPerson() {

        when(personRepository.findByCpfCnpj("12345678900"))
            .thenReturn(Optional.empty());

        when(personRepository.findIncludingInactiveByCpfCnpj("12345678900"))
            .thenReturn(Optional.empty());

        Person result =
            service.getOrCreateForRegistration(
                "123.456.789-00"
            );

        assertThat(result.getCpfCnpj())
            .isEqualTo("12345678900");

        assertThat(result.isActive())
            .isTrue();

        verify(personRepository)
            .findByCpfCnpj("12345678900");

        verify(personRepository)
            .findIncludingInactiveByCpfCnpj("12345678900");
    }

    @Test
    @DisplayName("Não deve criar nova Person quando CPF/CNPJ pertencer a cadastro inativado")
    void naoDeveCriarNovaPersonQuandoCpfCnpjPertencerACadastroInativado() {

        Person inactivePerson =
            buildPerson(10L, "12345678900", false);

        when(personRepository.findByCpfCnpj("12345678900"))
            .thenReturn(Optional.empty());

        when(personRepository.findIncludingInactiveByCpfCnpj("12345678900"))
            .thenReturn(Optional.of(inactivePerson));

        assertThatThrownBy(() ->
            service.getOrCreateForRegistration("12345678900")
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "Já existe um cadastro inativado para este CPF/CNPJ. " +
                "Solicite a reativação ao Suporte."
            );

        verify(personRepository)
            .findByCpfCnpj("12345678900");

        verify(personRepository)
            .findIncludingInactiveByCpfCnpj("12345678900");

        verify(personRepository, never())
            .save(any(Person.class));
    }

    @Test
    @DisplayName("Deve permitir manter o próprio CPF/CNPJ durante atualização")
    void devePermitirManterOProprioCpfCnpjDuranteAtualizacao() {

        Person person =
            buildPerson(10L, "12345678900", true);

        PersonRequestDto dto =
            new PersonRequestDto(
                "123.456.789-00",
                "PF",
                "João da Silva"
            );

        when(personRepository.findIncludingInactiveByCpfCnpj(
            "12345678900"
        )).thenReturn(Optional.of(person));

        service.updateFromDto(person, dto);

        assertThat(person.getCpfCnpj())
            .isEqualTo("12345678900");

        assertThat(person.getTipoPessoa())
            .isEqualTo(TipoPessoa.PF);

        assertThat(person.getName())
            .isEqualTo("João da Silva");

        verify(personRepository)
            .findIncludingInactiveByCpfCnpj("12345678900");
    }

    @Test
    @DisplayName("Não deve permitir CPF/CNPJ pertencente a outra Person")
    void naoDevePermitirCpfCnpjPertencenteAOutraPerson() {

        Person currentPerson =
            buildPerson(10L, "12345678900", true);

        Person existingPerson =
            buildPerson(20L, "98765432100", true);

        PersonRequestDto dto =
            new PersonRequestDto(
                "987.654.321-00",
                "PF",
                "João da Silva"
            );

        when(personRepository.findIncludingInactiveByCpfCnpj(
            "98765432100"
        )).thenReturn(Optional.of(existingPerson));

        assertThatThrownBy(() ->
            service.updateFromDto(currentPerson, dto)
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "CPF/CNPJ já cadastrado para outra pessoa no sistema."
            );

        assertThat(currentPerson.getCpfCnpj())
            .isEqualTo("12345678900");

        verify(personRepository)
            .findIncludingInactiveByCpfCnpj("98765432100");
    }

    @Test
    @DisplayName("Deve permitir CPF/CNPJ quando não existir outra Person")
    void devePermitirCpfCnpjQuandoNaoExistirOutraPerson() {

        Person person =
            buildPerson(10L, "12345678900", true);

        PersonRequestDto dto =
            new PersonRequestDto(
                "987.654.321-00",
                "PF",
                "João da Silva Atualizado"
            );

        when(personRepository.findIncludingInactiveByCpfCnpj(
            "98765432100"
        )).thenReturn(Optional.empty());

        service.updateFromDto(person, dto);

        assertThat(person.getCpfCnpj())
            .isEqualTo("98765432100");

        assertThat(person.getTipoPessoa())
            .isEqualTo(TipoPessoa.PF);

        assertThat(person.getName())
            .isEqualTo("João da Silva Atualizado");

        verify(personRepository)
            .findIncludingInactiveByCpfCnpj("98765432100");
    }

    @Test
    @DisplayName("Deve reativar Person inativa")
    void deveReativarPersonInativa() {

        Person person =
            buildPerson(10L, "12345678900", false);

        when(personRepository.findIncludingInactiveById(10L))
            .thenReturn(Optional.of(person));

        when(personRepository.save(person))
            .thenReturn(person);

        Person result =
            service.reactivate(10L);

        assertThat(result)
            .isSameAs(person);

        assertThat(result.isActive())
            .isTrue();

        verify(personRepository)
            .findIncludingInactiveById(10L);

        verify(personRepository)
            .save(person);
    }

    @Test
    @DisplayName("Deve manter Person ativa ao solicitar reativação")
    void deveManterPersonAtivaAoSolicitarReativacao() {

        Person person =
            buildPerson(10L, "12345678900", true);

        when(personRepository.findIncludingInactiveById(10L))
            .thenReturn(Optional.of(person));

        when(personRepository.save(person))
            .thenReturn(person);

        Person result =
            service.reactivate(10L);

        assertThat(result)
            .isSameAs(person);

        assertThat(result.isActive())
            .isTrue();

        verify(personRepository)
            .findIncludingInactiveById(10L);

        verify(personRepository)
            .save(person);
    }

    @Test
    @DisplayName("Deve lançar exceção ao reativar Person inexistente")
    void deveLancarExcecaoAoReativarPersonInexistente() {

        when(personRepository.findIncludingInactiveById(999L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.reactivate(999L)
        )
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage(
                "Pessoa não encontrada com o ID: 999"
            );

        verify(personRepository)
            .findIncludingInactiveById(999L);

        verify(personRepository, never())
            .save(any(Person.class));
    }

    @Test
    @DisplayName("Deve atualizar Person com dados do DTO")
    void deveAtualizarPersonComDadosDoDto() {

        Person person =
            buildPerson(10L, "12345678900", true);

        PersonRequestDto dto =
            new PersonRequestDto(
                "987.654.321-00",
                "PF",
                "João da Silva Atualizado"
            );

        when(personRepository.findIncludingInactiveByCpfCnpj(
            "98765432100"
        )).thenReturn(Optional.empty());

        service.updateFromDto(person, dto);

        assertThat(person.getCpfCnpj())
            .isEqualTo("98765432100");

        assertThat(person.getTipoPessoa())
            .isEqualTo(TipoPessoa.PF);

        assertThat(person.getName())
            .isEqualTo("João da Silva Atualizado");

        verify(personRepository)
            .findIncludingInactiveByCpfCnpj("98765432100");
    }

    @Test
    @DisplayName("Deve atualizar Person para PJ")
    void deveAtualizarPersonParaPj() {

        Person person =
            buildPerson(10L, "12345678900", true);

        PersonRequestDto dto =
            new PersonRequestDto(
                "12.345.678/0001-95",
                "PJ",
                "Fornecedor Tech Ltda"
            );

        when(personRepository.findIncludingInactiveByCpfCnpj(
            "12345678000195"
        )).thenReturn(Optional.empty());

        service.updateFromDto(person, dto);

        assertThat(person.getCpfCnpj())
            .isEqualTo("12345678000195");

        assertThat(person.getTipoPessoa())
            .isEqualTo(TipoPessoa.PJ);

        assertThat(person.getName())
            .isEqualTo("Fornecedor Tech Ltda");

        verify(personRepository)
            .findIncludingInactiveByCpfCnpj("12345678000195");
    }

    @Test
    @DisplayName("Deve rejeitar tipo de pessoa inválido")
    void deveRejeitarTipoDePessoaInvalido() {

        Person person =
            buildPerson(10L, "12345678900", true);

        PersonRequestDto dto =
            new PersonRequestDto(
                "123.456.789-00",
                "TIPO_INVALIDO",
                "João da Silva"
            );

        when(personRepository.findIncludingInactiveByCpfCnpj(
            "12345678900"
        )).thenReturn(Optional.of(person));

        assertThatThrownBy(() ->
            service.updateFromDto(person, dto)
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "Tipo de pessoa inválido: TIPO_INVALIDO"
            );
    }
}
