package net.ddns.lexdev.systempro_api.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import net.ddns.lexdev.systempro_api.domain.LegalEntity;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.PersonAddress;
import net.ddns.lexdev.systempro_api.domain.PersonContact;
import net.ddns.lexdev.systempro_api.domain.Supplier;
import net.ddns.lexdev.systempro_api.dto.BankDetailsDto;
import net.ddns.lexdev.systempro_api.dto.IndividualPersonRequestDto;
import net.ddns.lexdev.systempro_api.dto.LegalEntityRequestDto;
import net.ddns.lexdev.systempro_api.dto.PersonAddressRequestDto;
import net.ddns.lexdev.systempro_api.dto.PersonContactRequestDto;
import net.ddns.lexdev.systempro_api.dto.PersonRequestDto;
import net.ddns.lexdev.systempro_api.dto.SupplierContactDto;
import net.ddns.lexdev.systempro_api.dto.SupplierRequestDto;
import net.ddns.lexdev.systempro_api.dto.SupplierResponseDto;
import net.ddns.lexdev.systempro_api.enums.AddressType;
import net.ddns.lexdev.systempro_api.enums.ContactType;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.SupplierRepository;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private PersonService personService;

    private PersonContactService personContactService;
    private PersonAddressService personAddressService;

    private SupplierService service;
    @BeforeEach
    void setUp() {
        personContactService = new PersonContactService();
        personAddressService = new PersonAddressService();

        service = new SupplierService(
            supplierRepository,
            personService,
            personContactService,
            personAddressService
        );
    }

    private SupplierRequestDto buildSupplierRequestDto(String cpfCnpj) {

        PersonRequestDto personDto = new PersonRequestDto(
            cpfCnpj,
            "PJ",
            "Fornecedor Tech Ltda"
        );

        LegalEntityRequestDto legalEntity =
            new LegalEntityRequestDto(
                "Tech Fornecimentos",
                "123456789"
            );

        return new SupplierRequestDto(
            personDto,
            null,
            legalEntity,
            List.of(
                new PersonContactRequestDto(
                    "EMAIL",
                    "contato@techfornecimentos.com",
                    true,
                    null
                ),
                new PersonContactRequestDto(
                    "WHATSAPP",
                    "31988888888",
                    false,
                    null
                )
            ),
            List.of(
                new PersonAddressRequestDto(
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
            ),
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

    private Person buildPerson(Long id, String cpfCnpj) {

        Person person = new Person();

        ReflectionTestUtils.setField(
            person,
            "id",
            id
        );

        person.setTipoPessoa(TipoPessoa.PJ);
        person.setName("Fornecedor Tech Ltda");
        person.setCpfCnpj(cpfCnpj);
        person.setActive(true);

        LegalEntity legalEntity = new LegalEntity();
        legalEntity.setPerson(person);
        legalEntity.setNomeFantasia("Tech Fornecimentos");
        legalEntity.setInscricaoEstadual("123456789");

        person.setLegalEntity(legalEntity);

        PersonContact email = new PersonContact();
        email.setPerson(person);
        email.setType(ContactType.EMAIL);
        email.setValue("contato@techfornecimentos.com");
        email.setPrincipal(true);

        PersonContact whatsapp = new PersonContact();
        whatsapp.setPerson(person);
        whatsapp.setType(ContactType.WHATSAPP);
        whatsapp.setValue("31988888888");
        whatsapp.setPrincipal(false);

        person.setContacts(
            new ArrayList<>(List.of(
                email,
                whatsapp
            ))
        );

        PersonAddress address = new PersonAddress();
        address.setPerson(person);
        address.setType(AddressType.COMERCIAL);
        address.setCep("30100000");
        address.setLogradouro("Rua dos Fornecedores");
        address.setNumero("500");
        address.setComplemento("Sala 101");
        address.setBairro("Centro");
        address.setCidade("Belo Horizonte");
        address.setUf("MG");
        address.setPrincipal(true);

        person.setAddresses(
            new ArrayList<>(List.of(address))
        );

        return person;
    }

    private Supplier buildSupplier(Long id, Person person) {

        Supplier supplier = new Supplier();

        ReflectionTestUtils.setField(
            supplier,
            "id",
            id
        );

        supplier.setPerson(person);
        supplier.setCondicaoPagamentoPadrao("30 DIAS");
        supplier.setPrazoEntregaDias(5);
        supplier.setValorMinimoPedido(
            new BigDecimal("1000.00")
        );
        supplier.setCategoria("Tecnologia");
        supplier.setObservacoesComerciais(
            "Prazo de entrega rigoroso"
        );
        supplier.setActive(true);

        return supplier;
    }

    @Test
    @DisplayName("Deve criar fornecedor com CPF/CNPJ sanitizado")
    void deveCriarFornecedorComCpfCnpjSanitizado() {

        SupplierRequestDto dto =
            buildSupplierRequestDto(
                "12.345.678/0001-95"
            );

        Person person =
            buildPerson(
                10L,
                "12345678000195"
            );

        Supplier supplierSalvo =
            buildSupplier(
                1L,
                person
            );

        when(supplierRepository.existsByPersonCpfCnpj(
            "12345678000195"
        )).thenReturn(false);

        when(personService.getOrCreateForRegistration(
            "12345678000195"
        )).thenReturn(person);

        doNothing()
            .when(personService)
            .updateFromDto(
                person,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );

        when(supplierRepository.save(
            any(Supplier.class)
        )).thenReturn(supplierSalvo);

        SupplierResponseDto result =
            service.create(dto);

        ArgumentCaptor<Supplier> supplierCaptor =
            ArgumentCaptor.forClass(Supplier.class);

        verify(supplierRepository)
            .save(supplierCaptor.capture());

        Supplier supplierEnviadoParaRepository =
            supplierCaptor.getValue();

        assertThat(
            supplierEnviadoParaRepository.getPerson()
        ).isEqualTo(person);

        assertThat(
            supplierEnviadoParaRepository
                .getPerson()
                .getCpfCnpj()
        ).isEqualTo("12345678000195");

        assertThat(result.id())
            .isEqualTo(1L);

        assertThat(result.person().name())
            .isEqualTo("Fornecedor Tech Ltda");

        assertThat(result.person().cpfCnpj())
            .isEqualTo("12345678000195");

        assertThat(result.legalEntity().nomeFantasia())
            .isEqualTo("Tech Fornecimentos");

        verify(supplierRepository)
            .existsByPersonCpfCnpj(
                "12345678000195"
            );

        verify(personService)
            .getOrCreateForRegistration(
                "12345678000195"
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
    @DisplayName(
        "Não deve criar fornecedor quando CPF/CNPJ já estiver cadastrado"
    )
    void naoDeveCriarFornecedorQuandoCpfCnpjJaExiste() {

        SupplierRequestDto dto =
            buildSupplierRequestDto(
                "12.345.678/0001-95"
            );

        when(supplierRepository.existsByPersonCpfCnpj(
            "12345678000195"
        )).thenReturn(true);

        assertThatThrownBy(
            () -> service.create(dto)
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "Esta pessoa/empresa já está cadastrada como fornecedor ativo."
            );

        verify(supplierRepository)
            .existsByPersonCpfCnpj(
                "12345678000195"
            );

        verify(supplierRepository, never())
            .save(any(Supplier.class));

        verify(personService, never())
            .getOrCreateForRegistration(anyString());
    }

    @Test
    @DisplayName(
        "Não deve criar fornecedor quando já existir Person inativa com o CPF/CNPJ"
    )
    void naoDeveCriarFornecedorQuandoJaExistirPersonInativa() {

        SupplierRequestDto dto =
            buildSupplierRequestDto(
                "12.345.678/0001-95"
            );

        when(supplierRepository.existsByPersonCpfCnpj(
            "12345678000195"
        )).thenReturn(false);

        when(personService.getOrCreateForRegistration(
            "12345678000195"
        ))
            .thenThrow(
                new BusinessException(
                    "Já existe um cadastro inativado para este CPF/CNPJ. " +
                    "Solicite a reativação ao Suporte."
                )
            );

        assertThatThrownBy(
            () -> service.create(dto)
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "Já existe um cadastro inativado para este CPF/CNPJ. " +
                "Solicite a reativação ao Suporte."
            );

        verify(supplierRepository)
            .existsByPersonCpfCnpj(
                "12345678000195"
            );

        verify(personService)
            .getOrCreateForRegistration(
                "12345678000195"
            );

        verify(supplierRepository, never())
            .save(any(Supplier.class));
    }

    @Test
    @DisplayName("Deve retornar fornecedor quando o ID existir")
    void deveRetornarFornecedorQuandoIdExistir() {

        Person person =
            buildPerson(
                10L,
                "12345678000195"
            );

        Supplier supplier =
            buildSupplier(
                1L,
                person
            );

        when(supplierRepository.findByIdWithPerson(1L))
            .thenReturn(Optional.of(supplier));

        SupplierResponseDto result =
            service.findById(1L);

        assertThat(result)
            .isNotNull();

        assertThat(result.id())
            .isEqualTo(1L);

        assertThat(result.person().tipoPessoa())
            .isEqualTo("PJ");

        assertThat(result.person().name())
            .isEqualTo("Fornecedor Tech Ltda");

        assertThat(result.person().cpfCnpj())
            .isEqualTo("12345678000195");

        assertThat(result.legalEntity().nomeFantasia())
            .isEqualTo("Tech Fornecimentos");

        assertThat(result.legalEntity().inscricaoEstadual())
            .isEqualTo("123456789");

        assertThat(result.contacts())
            .hasSize(2);

        assertThat(result.addresses())
            .hasSize(1);

        assertThat(result.condicaoPagamentoPadrao())
            .isEqualTo("30 DIAS");

        verify(supplierRepository)
            .findByIdWithPerson(1L);
    }

    @Test
    @DisplayName(
        "Deve lançar exceção quando o fornecedor não existir"
    )
    void deveLancarExcecaoQuandoFornecedorNaoExistir() {

        when(supplierRepository.findByIdWithPerson(999L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> service.findById(999L)
        )
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage(
                "Fornecedor não encontrado com o ID: 999"
            );

        verify(supplierRepository)
            .findByIdWithPerson(999L);
    }

    @Test
    @DisplayName(
        "Deve atualizar fornecedor quando os dados forem válidos"
    )
    void deveAtualizarFornecedorQuandoDadosForemValidos() {

        Person person =
            buildPerson(
                10L,
                "12345678000195"
            );

        Supplier supplier =
            buildSupplier(
                1L,
                person
            );

        PersonRequestDto personDto =
            new PersonRequestDto(
                "98.765.432/0001-10",
                "PJ",
                "Fornecedor Tech Ltda Atualizado"
            );

        LegalEntityRequestDto legalEntity =
            new LegalEntityRequestDto(
                "Tech Fornecimentos ME",
                "987654321"
            );

        SupplierRequestDto dto =
            new SupplierRequestDto(
                personDto,
                null,
                legalEntity,
                List.of(),
                List.of(),
                "60 DIAS",
                10,
                new BigDecimal("2000.00"),
                "Tecnologia e Serviços",
                "Parceria Estratégica",
                null,
                List.of(),
                List.of(),
                true
            );

        when(supplierRepository.findByIdWithPerson(1L))
            .thenReturn(Optional.of(supplier));

        doNothing()
            .when(personService)
            .updateFromDto(
                person,
                personDto,
                null,
                legalEntity
            );

        when(supplierRepository.save(
            any(Supplier.class)
        ))
            .thenAnswer(
                invocation -> invocation.getArgument(0)
            );

        SupplierResponseDto result =
            service.update(1L, dto);

        assertThat(result)
            .isNotNull();

        assertThat(result.id())
            .isEqualTo(1L);

        verify(supplierRepository)
            .findByIdWithPerson(1L);

        verify(personService)
            .updateFromDto(
                person,
                personDto,
                null,
                legalEntity
            );

        verify(supplierRepository)
            .save(supplier);
    }

    @Test
    @DisplayName(
        "Não deve atualizar quando CPF/CNPJ pertencer a outra pessoa"
    )
    void naoDeveAtualizarQuandoCpfCnpjPertencerAOutraPessoa() {

        Person personCurrent =
            buildPerson(
                10L,
                "12345678000195"
            );

        Supplier supplier =
            buildSupplier(
                1L,
                personCurrent
            );

        SupplierRequestDto dto =
            buildSupplierRequestDto(
                "98.765.432/0001-10"
            );

        when(supplierRepository.findByIdWithPerson(1L))
            .thenReturn(Optional.of(supplier));

        doThrow(
            new BusinessException(
                "CPF/CNPJ já cadastrado para outra pessoa no sistema."
            )
        )
            .when(personService)
            .updateFromDto(
                personCurrent,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );

        assertThatThrownBy(
            () -> service.update(1L, dto)
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage(
                "CPF/CNPJ já cadastrado para outra pessoa no sistema."
            );

        verify(supplierRepository)
            .findByIdWithPerson(1L);

        verify(personService)
            .updateFromDto(
                personCurrent,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );

        verify(supplierRepository, never())
            .save(any(Supplier.class));
    }

    @Test
    @DisplayName(
        "Deve permitir atualizar o fornecedor mantendo seu próprio CPF/CNPJ"
    )
    void devePermitirAtualizarMantendoProprioCpfCnpj() {

        Person person =
            buildPerson(
                10L,
                "12345678000195"
            );

        Supplier supplier =
            buildSupplier(
                1L,
                person
            );

        SupplierRequestDto dto =
            buildSupplierRequestDto(
                "12.345.678/0001-95"
            );

        when(supplierRepository.findByIdWithPerson(1L))
            .thenReturn(Optional.of(supplier));

        doNothing()
            .when(personService)
            .updateFromDto(
                person,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );

        when(supplierRepository.save(
            any(Supplier.class)
        ))
            .thenAnswer(
                invocation -> invocation.getArgument(0)
            );

        SupplierResponseDto result =
            service.update(1L, dto);

        assertThat(result)
            .isNotNull();

        assertThat(result.id())
            .isEqualTo(1L);

        verify(supplierRepository)
            .findByIdWithPerson(1L);

        verify(personService)
            .updateFromDto(
                person,
                dto.person(),
                dto.individual(),
                dto.legalEntity()
            );

        verify(supplierRepository)
            .save(supplier);
    }

    @Test
    @DisplayName(
        "Deve lançar exceção ao atualizar fornecedor inexistente"
    )
    void deveLancarExcecaoAoAtualizarFornecedorInexistente() {

        SupplierRequestDto dto =
            buildSupplierRequestDto(
                "12.345.678/0001-95"
            );

        when(supplierRepository.findByIdWithPerson(999L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> service.update(999L, dto)
        )
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage(
                "Fornecedor não encontrado com o ID: 999"
            );

        verify(supplierRepository)
            .findByIdWithPerson(999L);

        verify(personService, never())
            .updateFromDto(
                any(Person.class),
                any(PersonRequestDto.class),
                any(IndividualPersonRequestDto.class),
                any(LegalEntityRequestDto.class)
            );

        verify(supplierRepository, never())
            .save(any(Supplier.class));
    }

    @Test
    @DisplayName(
        "Deve realizar soft delete do fornecedor sem inativar a Person"
    )
    void deveRealizarSoftDeleteDoFornecedor() {

        Person person =
            buildPerson(
                10L,
                "12345678000195"
            );

        Supplier supplier =
            buildSupplier(
                1L,
                person
            );

        when(supplierRepository.findByIdWithPerson(1L))
            .thenReturn(Optional.of(supplier));

        service.delete(1L);

        assertThat(supplier.isActive())
            .isFalse();

        assertThat(person.isActive())
            .isTrue();

        verify(supplierRepository)
            .findByIdWithPerson(1L);

        verify(supplierRepository)
            .save(supplier);

        verify(personService, never())
            .reactivate(anyLong());
    }

    @Test
    @DisplayName(
        "Deve lançar exceção ao excluir fornecedor inexistente"
    )
    void deveLancarExcecaoAoExcluirFornecedorInexistente() {

        when(supplierRepository.findByIdWithPerson(999L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> service.delete(999L)
        )
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage(
                "Fornecedor não encontrado com o ID: 999"
            );

        verify(supplierRepository)
            .findByIdWithPerson(999L);

        verify(supplierRepository, never())
            .save(any(Supplier.class));
    }

    @Test
    @DisplayName("Deve retornar fornecedores paginados")
    void deveRetornarFornecedoresPaginados() {

        Person person1 =
            buildPerson(
                10L,
                "12345678000195"
            );

        Supplier supplier1 =
            buildSupplier(
                1L,
                person1
            );

        Person person2 =
            buildPerson(
                20L,
                "98765432000110"
            );

        Supplier supplier2 =
            buildSupplier(
                2L,
                person2
            );

        Pageable pageable =
            PageRequest.of(0, 10);

        Page<Supplier> supplierPage =
            new PageImpl<>(
                List.of(
                    supplier1,
                    supplier2
                ),
                pageable,
                2
            );

        when(supplierRepository.findAllWithPerson(pageable))
            .thenReturn(supplierPage);

        Page<SupplierResponseDto> result =
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
                .get(0)
                .person()
                .cpfCnpj()
        )
            .isEqualTo("12345678000195");

        assertThat(
            result.getContent()
                .get(1)
                .id()
        )
            .isEqualTo(2L);

        assertThat(
            result.getContent()
                .get(1)
                .person()
                .cpfCnpj()
        )
            .isEqualTo("98765432000110");

        assertThat(result.getTotalElements())
            .isEqualTo(2);

        verify(supplierRepository)
            .findAllWithPerson(pageable);
    }

    @Test
    @DisplayName(
        "Deve retornar página vazia quando não houver fornecedores"
    )
    void deveRetornarPaginaVaziaQuandoNaoHouverFornecedores() {

        Pageable pageable =
            PageRequest.of(0, 10);

        when(supplierRepository.findAllWithPerson(pageable))
            .thenReturn(Page.empty(pageable));

        Page<SupplierResponseDto> result =
            service.findAll(pageable);

        assertThat(result)
            .isNotNull();

        assertThat(result.getContent())
            .isEmpty();

        assertThat(result.getTotalElements())
            .isZero();

        verify(supplierRepository)
            .findAllWithPerson(pageable);
    }
}