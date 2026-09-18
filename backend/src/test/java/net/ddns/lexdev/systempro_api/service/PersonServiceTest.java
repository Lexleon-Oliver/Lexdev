package net.ddns.lexdev.systempro_api.service;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import net.ddns.lexdev.systempro_api.domain.IndividualPerson;
import net.ddns.lexdev.systempro_api.domain.LegalEntity;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.dto.IndividualPersonRequestDto;
import net.ddns.lexdev.systempro_api.dto.LegalEntityRequestDto;
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

        ReflectionTestUtils.setField(
            person,
            "id",
            id
        );

        person.setCpfCnpj(cpfCnpj);
        person.setActive(active);

        return person;
    }

    @Test
    @DisplayName("Deve retornar Person ativa quando CPF/CNPJ já estiver cadastrado")
    void deveRetornarPersonAtivaQuandoCpfCnpjJaEstiverCadastrado() {

        Person person =
            buildPerson(1L, "12345678900", true);

        when(
            personRepository.findByCpfCnpj(
                "12345678900"
            )
        )
            .thenReturn(Optional.of(person));

        Person result =
            service.getOrCreateForRegistration(
                "12345678900"
            );

        assertThat(result)
            .isSameAs(person);

        assertThat(result.isActive())
            .isTrue();

        verify(personRepository)
            .findByCpfCnpj("12345678900");

        verify(
            personRepository,
            never()
        )
            .findIncludingInactiveByCpfCnpj(
                "12345678900"
            );
    }

    @Test
    @DisplayName("Deve criar nova Person quando CPF/CNPJ não existir")
    void deveCriarNovaPersonQuandoCpfCnpjNaoExistir() {

        when(
            personRepository.findByCpfCnpj(
                "12345678900"
            )
        )
            .thenReturn(Optional.empty());

        when(
            personRepository.findIncludingInactiveByCpfCnpj(
                "12345678900"
            )
        )
            .thenReturn(Optional.empty());

        Person result =
            service.getOrCreateForRegistration(
                "12345678900"
            );

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
            .findIncludingInactiveByCpfCnpj(
                "12345678900"
            );

        verify(
            personRepository,
            never()
        )
            .save(any(Person.class));
    }

    @Test
    @DisplayName("Deve normalizar CPF/CNPJ ao criar nova Person")
    void deveNormalizarCpfCnpjAoCriarNovaPerson() {

        when(
            personRepository.findByCpfCnpj(
                "12345678900"
            )
        )
            .thenReturn(Optional.empty());

        when(
            personRepository.findIncludingInactiveByCpfCnpj(
                "12345678900"
            )
        )
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
            .findIncludingInactiveByCpfCnpj(
                "12345678900"
            );
    }

    @Test
    @DisplayName("Não deve criar nova Person quando CPF/CNPJ pertencer a cadastro inativado")
    void naoDeveCriarNovaPersonQuandoCpfCnpjPertencerACadastroInativado() {

        Person inactivePerson =
            buildPerson(
                10L,
                "12345678900",
                false
            );

        when(
            personRepository.findByCpfCnpj(
                "12345678900"
            )
        )
            .thenReturn(Optional.empty());

        when(
            personRepository.findIncludingInactiveByCpfCnpj(
                "12345678900"
            )
        )
            .thenReturn(Optional.of(inactivePerson));

        assertThatThrownBy(() ->
            service.getOrCreateForRegistration(
                "12345678900"
            )
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "Já existe um cadastro inativado para este CPF/CNPJ. " +
                "Solicite a reativação ao Suporte."
            );

        verify(personRepository)
            .findByCpfCnpj("12345678900");

        verify(personRepository)
            .findIncludingInactiveByCpfCnpj(
                "12345678900"
            );

        verify(
            personRepository,
            never()
        )
            .save(any(Person.class));
    }

    @Test
    @DisplayName("Deve permitir manter o próprio CPF/CNPJ durante atualização")
    void devePermitirManterOProprioCpfCnpjDuranteAtualizacao() {

        Person person =
            buildPerson(
                10L,
                "52998224725",
                true
            );

        PersonRequestDto dto =
            new PersonRequestDto(
                "529.982.247-25",
                "PF",
                "João da Silva"
            );

        IndividualPersonRequestDto individualDto =
            new IndividualPersonRequestDto(
                "495493478"
            );

        when(
            personRepository.findIncludingInactiveByCpfCnpj(
                "52998224725"
            )
        )
            .thenReturn(Optional.of(person));

        service.updateFromDto(
            person,
            dto,
            individualDto,
            null
        );

        assertThat(person.getCpfCnpj())
            .isEqualTo("52998224725");

        assertThat(person.getTipoPessoa())
            .isEqualTo(TipoPessoa.PF);

        assertThat(person.getName())
            .isEqualTo("João da Silva");

        verify(personRepository)
            .findIncludingInactiveByCpfCnpj(
                "52998224725"
            );
    }

    @Test
    @DisplayName("Não deve permitir CPF/CNPJ pertencente a outra Person")
    void naoDevePermitirCpfCnpjPertencenteAOutraPerson() {

        Person currentPerson =
            buildPerson(
                10L,
                "12345678900",
                true
            );

        Person existingPerson =
            buildPerson(
                20L,
                "98765432100",
                true
            );

        PersonRequestDto dto =
            new PersonRequestDto(
                "987.654.321-00",
                "PF",
                "João da Silva"
            );

        IndividualPersonRequestDto individualDto =
            new IndividualPersonRequestDto(
                "495493478"
            );

        when(
            personRepository.findIncludingInactiveByCpfCnpj(
                "98765432100"
            )
        )
            .thenReturn(Optional.of(existingPerson));

        assertThatThrownBy(() ->
            service.updateFromDto(
                currentPerson,
                dto,
                individualDto,
                null
            )
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "CPF/CNPJ já cadastrado para outra pessoa no sistema."
            );

        assertThat(currentPerson.getCpfCnpj())
            .isEqualTo("12345678900");

        verify(personRepository)
            .findIncludingInactiveByCpfCnpj(
                "98765432100"
            );
    }

    @Test
    @DisplayName("Deve permitir CPF/CNPJ quando não existir outra Person")
    void devePermitirCpfCnpjQuandoNaoExistirOutraPerson() {

        Person person =
            buildPerson(
                10L,
                "12345678900",
                true
            );

        PersonRequestDto dto =
            new PersonRequestDto(
                "987.654.321-00",
                "PF",
                "João da Silva Atualizado"
            );

        IndividualPersonRequestDto individualDto =
            new IndividualPersonRequestDto(
                "495493478"
            );

        when(
            personRepository.findIncludingInactiveByCpfCnpj(
                "98765432100"
            )
        )
            .thenReturn(Optional.empty());

        service.updateFromDto(
            person,
            dto,
            individualDto,
            null
        );

        assertThat(person.getCpfCnpj())
            .isEqualTo("98765432100");

        assertThat(person.getTipoPessoa())
            .isEqualTo(TipoPessoa.PF);

        assertThat(person.getName())
            .isEqualTo("João da Silva Atualizado");

        verify(personRepository)
            .findIncludingInactiveByCpfCnpj(
                "98765432100"
            );
    }

    @Test
    @DisplayName("Deve reativar Person inativa")
    void deveReativarPersonInativa() {

        Person person =
            buildPerson(
                10L,
                "12345678900",
                false
            );

        when(
            personRepository.findIncludingInactiveById(10L)
        )
            .thenReturn(Optional.of(person));

        when(
            personRepository.save(person)
        )
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
            buildPerson(
                10L,
                "12345678900",
                true
            );

        when(
            personRepository.findIncludingInactiveById(10L)
        )
            .thenReturn(Optional.of(person));

        when(
            personRepository.save(person)
        )
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

        when(
            personRepository.findIncludingInactiveById(999L)
        )
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

        verify(
            personRepository,
            never()
        )
            .save(any(Person.class));
    }

    @Test
    @DisplayName("Deve atualizar Person com dados do DTO")
    void deveAtualizarPersonComDadosDoDto() {

        Person person =
            buildPerson(
                10L,
                "12345678900",
                true
            );

        PersonRequestDto dto =
            new PersonRequestDto(
                "987.654.321-00",
                "PF",
                "João da Silva Atualizado"
            );

        IndividualPersonRequestDto individualDto =
            new IndividualPersonRequestDto(
                "495493478"
            );

        when(
            personRepository.findIncludingInactiveByCpfCnpj(
                "98765432100"
            )
        )
            .thenReturn(Optional.empty());

        service.updateFromDto(
            person,
            dto,
            individualDto,
            null
        );

        assertThat(person.getCpfCnpj())
            .isEqualTo("98765432100");

        assertThat(person.getTipoPessoa())
            .isEqualTo(TipoPessoa.PF);

        assertThat(person.getName())
            .isEqualTo("João da Silva Atualizado");

        verify(personRepository)
            .findIncludingInactiveByCpfCnpj(
                "98765432100"
            );
    }

    @Test
    @DisplayName("Deve atualizar Person para PJ")
    void deveAtualizarPersonParaPj() {

        Person person =
            buildPerson(
                10L,
                "12345678900",
                true
            );

        PersonRequestDto dto =
            new PersonRequestDto(
                "12.345.678/0001-95",
                "PJ",
                "Fornecedor Tech Ltda"
            );

        LegalEntityRequestDto legalEntityDto =
            new LegalEntityRequestDto(
                "Fornecedor Tech",
                "123456789"
            );

        when(
            personRepository.findIncludingInactiveByCpfCnpj(
                "12345678000195"
            )
        )
            .thenReturn(Optional.empty());

        service.updateFromDto(
            person,
            dto,
            null,
            legalEntityDto
        );

        assertThat(person.getCpfCnpj())
            .isEqualTo("12345678000195");

        assertThat(person.getTipoPessoa())
            .isEqualTo(TipoPessoa.PJ);

        assertThat(person.getName())
            .isEqualTo("Fornecedor Tech Ltda");

        verify(personRepository)
            .findIncludingInactiveByCpfCnpj(
                "12345678000195"
            );
    }

    @Test
    @DisplayName("Deve rejeitar tipo de pessoa inválido")
    void deveRejeitarTipoDePessoaInvalido() {

        Person person =
            buildPerson(
                10L,
                "12345678900",
                true
            );

        PersonRequestDto dto =
            new PersonRequestDto(
                "123.456.789-00",
                "TIPO_INVALIDO",
                "João da Silva"
            );

        IndividualPersonRequestDto individualDto =
            new IndividualPersonRequestDto(
                "495493478"
            );

        when(
            personRepository.findIncludingInactiveByCpfCnpj(
                "12345678900"
            )
        )
            .thenReturn(Optional.of(person));

        assertThatThrownBy(() ->
            service.updateFromDto(
                person,
                dto,
                individualDto,
                null
            )
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "Tipo de pessoa inválido: TIPO_INVALIDO"
            );
    }

    @Test
    void shouldUpdateIndividualPerson() {

        Person person = new Person();

        ReflectionTestUtils.setField(
            person,
            "id",
            1L
        );

        person.setTipoPessoa(TipoPessoa.PF);
        person.setCpfCnpj("52998224725");

        IndividualPerson individual =
            new IndividualPerson();

        individual.setPerson(person);
        individual.setRg("111111111");

        person.setIndividualPerson(individual);

        PersonRequestDto personDto =
            new PersonRequestDto(
                "529.982.247-25",
                "PF",
                "João Atualizado"
            );

        IndividualPersonRequestDto individualDto =
            new IndividualPersonRequestDto(
                "222222222"
            );

        LegalEntityRequestDto legalEntityDto = null;

        when(
            personRepository.findIncludingInactiveByCpfCnpj(
                "52998224725"
            )
        )
            .thenReturn(Optional.of(person));

        service.updateFromDto(
            person,
            personDto,
            individualDto,
            legalEntityDto
        );

        assertEquals(
            "João Atualizado",
            person.getName()
        );

        assertEquals(
            "222222222",
            person.getIndividualPerson().getRg()
        );

        assertNull(
            person.getLegalEntity()
        );
    }

    @Test
    void shouldUpdateLegalEntity() {

        Person person = new Person();

        ReflectionTestUtils.setField(
            person,
            "id",
            1L
        );

        person.setTipoPessoa(TipoPessoa.PJ);
        person.setCpfCnpj("11222333000181");

        LegalEntity legalEntity =
            new LegalEntity();

        legalEntity.setPerson(person);
        legalEntity.setNomeFantasia("Empresa Antiga");

        person.setLegalEntity(legalEntity);

        PersonRequestDto personDto =
            new PersonRequestDto(
                "11.222.333/0001-81",
                "PJ",
                "Empresa Atualizada"
            );

        IndividualPersonRequestDto individualDto = null;

        LegalEntityRequestDto legalEntityDto =
            new LegalEntityRequestDto(
                "Empresa Nova",
                "123456789"
            );

        when(
            personRepository.findIncludingInactiveByCpfCnpj(
                "11222333000181"
            )
        )
            .thenReturn(Optional.of(person));

        service.updateFromDto(
            person,
            personDto,
            individualDto,
            legalEntityDto
        );

        assertEquals(
            "Empresa Atualizada",
            person.getName()
        );

        assertEquals(
            "Empresa Nova",
            person.getLegalEntity().getNomeFantasia()
        );

        assertEquals(
            "123456789",
            person.getLegalEntity().getInscricaoEstadual()
        );

        assertNull(
            person.getIndividualPerson()
        );
    }

    @Test
    void shouldChangeIndividualPersonToLegalEntity() {

        Person person = new Person();

        ReflectionTestUtils.setField(
            person,
            "id",
            1L
        );

        person.setTipoPessoa(TipoPessoa.PF);
        person.setCpfCnpj("52998224725");

        IndividualPerson individual =
            new IndividualPerson();

        individual.setPerson(person);
        individual.setRg("111111111");

        person.setIndividualPerson(individual);

        PersonRequestDto personDto =
            new PersonRequestDto(
                "11.222.333/0001-81",
                "PJ",
                "Empresa Nova"
            );

        IndividualPersonRequestDto individualDto = null;

        LegalEntityRequestDto legalEntityDto =
            new LegalEntityRequestDto(
                "Empresa Nova",
                "123456789"
            );

        when(
            personRepository.findIncludingInactiveByCpfCnpj(
                "11222333000181"
            )
        )
            .thenReturn(Optional.empty());

        service.updateFromDto(
            person,
            personDto,
            individualDto,
            legalEntityDto
        );

        assertEquals(
            TipoPessoa.PJ,
            person.getTipoPessoa()
        );

        assertEquals(
            "11222333000181",
            person.getCpfCnpj()
        );

        assertNull(
            person.getIndividualPerson()
        );

        assertNotNull(
            person.getLegalEntity()
        );

        assertEquals(
            "Empresa Nova",
            person.getLegalEntity().getNomeFantasia()
        );
    }

    @Test
    void shouldChangeLegalEntityToIndividualPerson() {

        Person person = new Person();

        ReflectionTestUtils.setField(
            person,
            "id",
            1L
        );

        person.setTipoPessoa(TipoPessoa.PJ);
        person.setCpfCnpj("11222333000181");

        LegalEntity legalEntity =
            new LegalEntity();

        legalEntity.setPerson(person);
        legalEntity.setNomeFantasia("Empresa Antiga");

        person.setLegalEntity(legalEntity);

        PersonRequestDto personDto =
            new PersonRequestDto(
                "529.982.247-25",
                "PF",
                "Pessoa Física"
            );

        IndividualPersonRequestDto individualDto =
            new IndividualPersonRequestDto(
                "999999999"
            );

        LegalEntityRequestDto legalEntityDto = null;

        when(
            personRepository.findIncludingInactiveByCpfCnpj(
                "52998224725"
            )
        )
            .thenReturn(Optional.empty());

        service.updateFromDto(
            person,
            personDto,
            individualDto,
            legalEntityDto
        );

        assertEquals(
            TipoPessoa.PF,
            person.getTipoPessoa()
        );

        assertEquals(
            "52998224725",
            person.getCpfCnpj()
        );

        assertNull(
            person.getLegalEntity()
        );

        assertNotNull(
            person.getIndividualPerson()
        );

        assertEquals(
            "999999999",
            person.getIndividualPerson().getRg()
        );
    }

    @Test
    void shouldRejectIndividualPersonMissingForPf() {

        PersonRequestDto personDto =
            new PersonRequestDto(
                "12345678901",
                "PF",
                "João"
            );

        assertThrows(
            BusinessException.class,
            () -> service.updateFromDto(
                new Person(),
                personDto,
                null,
                null
            )
        );
    }

    @Test
    void shouldRejectLegalEntityMissingForPj() {

        PersonRequestDto personDto =
            new PersonRequestDto(
                "12345678000199",
                "PJ",
                "Empresa"
            );

        assertThrows(
            BusinessException.class,
            () -> service.updateFromDto(
                new Person(),
                personDto,
                null,
                null
            )
        );
    }
}