package net.ddns.lexdev.systempro_api.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.config.CpfCnpjNormalizer;
import net.ddns.lexdev.systempro_api.domain.BankDetails;
import net.ddns.lexdev.systempro_api.domain.IndividualPerson;
import net.ddns.lexdev.systempro_api.domain.LegalEntity;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.PersonAddress;
import net.ddns.lexdev.systempro_api.domain.PersonContact;
import net.ddns.lexdev.systempro_api.domain.Supplier;
import net.ddns.lexdev.systempro_api.domain.SupplierContact;
import net.ddns.lexdev.systempro_api.domain.SupplierDocument;
import net.ddns.lexdev.systempro_api.dto.IndividualPersonRequestDto;
import net.ddns.lexdev.systempro_api.dto.LegalEntityRequestDto;
import net.ddns.lexdev.systempro_api.dto.PersonAddressRequestDto;
import net.ddns.lexdev.systempro_api.dto.PersonContactRequestDto;
import net.ddns.lexdev.systempro_api.dto.SupplierRequestDto;
import net.ddns.lexdev.systempro_api.dto.SupplierResponseDto;
import net.ddns.lexdev.systempro_api.enums.AddressType;
import net.ddns.lexdev.systempro_api.enums.ContactType;
import net.ddns.lexdev.systempro_api.enums.TipoContaBancaria;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.SupplierRepository;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final PersonService personService;

    public SupplierService(
        SupplierRepository supplierRepository,
        PersonService personService
    ) {
        this.supplierRepository = supplierRepository;
        this.personService = personService;
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponseDto> findAll(Pageable pageable) {
        return supplierRepository.findAllWithPerson(pageable)
            .map(SupplierResponseDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public SupplierResponseDto findById(Long id) {

        Supplier supplier = supplierRepository.findByIdWithPerson(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Fornecedor não encontrado com o ID: " + id
            ));

        return SupplierResponseDto.fromEntity(supplier);
    }

    @Transactional
    public SupplierResponseDto create(SupplierRequestDto dto) {

        String cpfCnpj = CpfCnpjNormalizer.normalize(
            dto.person().cpfCnpj()
        );

        if (supplierRepository.existsByPersonCpfCnpj(cpfCnpj)) {
            throw new BusinessException(
                "Esta pessoa/empresa já está cadastrada como fornecedor ativo."
            );
        }

        Person person = personService.getOrCreateForRegistration(cpfCnpj);

        personService.updateFromDto(
            person,
            dto.person()
        );

        applyPersonSpecialization(person, dto);

        updatePersonContacts(person, dto.contacts());
        updatePersonAddresses(person, dto.addresses());

        Supplier supplier = new Supplier();

        supplier.setPerson(person);

        copyDtoToSupplier(dto, supplier);

        if (dto.active() != null) {
            supplier.setActive(dto.active());
        }

        return SupplierResponseDto.fromEntity(
            supplierRepository.save(supplier)
        );
    }

    @Transactional
    public SupplierResponseDto update(
        Long id,
        SupplierRequestDto dto
    ) {

        Supplier supplier = supplierRepository.findByIdWithPerson(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Fornecedor não encontrado com o ID: " + id
            ));

        Person person = supplier.getPerson();

        personService.updateFromDto(
            person,
            dto.person()
        );

        applyPersonSpecialization(person, dto);

        if (dto.contacts() != null) {
            updatePersonContacts(person, dto.contacts());
        }

        if (dto.addresses() != null) {
            updatePersonAddresses(person, dto.addresses());
        }

        copyDtoToSupplier(dto, supplier);

        if (dto.active() != null) {
            supplier.setActive(dto.active());
        }

        return SupplierResponseDto.fromEntity(
            supplierRepository.save(supplier)
        );
    }

    @Transactional
    public void delete(Long id) {

        Supplier supplier = supplierRepository.findByIdWithPerson(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Fornecedor não encontrado com o ID: " + id
            ));

        supplier.setActive(false);

        supplierRepository.save(supplier);
    }

    private void applyPersonSpecialization(
        Person person,
        SupplierRequestDto dto
    ) {

        TipoPessoa tipoPessoa = person.getTipoPessoa();

        if (tipoPessoa == null) {
            throw new BusinessException(
                "O tipo de pessoa deve ser informado."
            );
        }

        switch (tipoPessoa) {

            case PF -> applyIndividualPerson(
                person,
                dto.individual(),
                dto.legalEntity()
            );

            case PJ -> applyLegalEntity(
                person,
                dto.legalEntity(),
                dto.individual()
            );
        }
    }

    private void applyIndividualPerson(
        Person person,
        IndividualPersonRequestDto dto,
        LegalEntityRequestDto legalEntityDto
    ) {

        if (legalEntityDto != null) {
            throw new BusinessException(
                "Dados de pessoa jurídica não podem ser informados para uma pessoa física."
            );
        }

        if (dto == null) {
            throw new BusinessException(
                "Os dados de pessoa física devem ser informados."
            );
        }

        if (person.getLegalEntity() != null) {
            person.setLegalEntity(null);
        }

        IndividualPerson individual = person.getIndividualPerson();

        if (individual == null) {
            individual = new IndividualPerson();
            individual.setPerson(person);
            person.setIndividualPerson(individual);
        }

        individual.setRg(dto.rg());
    }

    private void applyLegalEntity(
        Person person,
        LegalEntityRequestDto dto,
        IndividualPersonRequestDto individualDto
    ) {

        if (individualDto != null) {
            throw new BusinessException(
                "Dados de pessoa física não podem ser informados para uma pessoa jurídica."
            );
        }

        if (dto == null) {
            throw new BusinessException(
                "Os dados de pessoa jurídica devem ser informados."
            );
        }

        if (person.getIndividualPerson() != null) {
            person.setIndividualPerson(null);
        }

        LegalEntity legalEntity = person.getLegalEntity();

        if (legalEntity == null) {
            legalEntity = new LegalEntity();
            legalEntity.setPerson(person);
            person.setLegalEntity(legalEntity);
        }

        legalEntity.setNomeFantasia(dto.nomeFantasia());
        legalEntity.setInscricaoEstadual(dto.inscricaoEstadual());
    }

    private void updatePersonContacts(
        Person person,
        List<PersonContactRequestDto> contactDtos
    ) {

        person.getContacts().clear();

        if (contactDtos == null) {
            return;
        }

        contactDtos.forEach(dto -> {

            PersonContact contact = new PersonContact();

            contact.setType(parseContactType(dto.type()));
            contact.setValue(dto.value());
            contact.setPrincipal(
                Boolean.TRUE.equals(dto.principal())
            );
            contact.setDescription(dto.description());

            person.addContact(contact);
        });
    }

    private void updatePersonAddresses(
        Person person,
        List<PersonAddressRequestDto> addressDtos
    ) {

        person.getAddresses().clear();

        if (addressDtos == null) {
            return;
        }

        addressDtos.forEach(dto -> {

            PersonAddress address = new PersonAddress();

            address.setType(parseAddressType(dto.type()));
            address.setCep(dto.cep());
            address.setLogradouro(dto.logradouro());
            address.setNumero(dto.numero());
            address.setComplemento(dto.complemento());
            address.setBairro(dto.bairro());
            address.setCidade(dto.cidade());
            address.setUf(dto.uf());
            address.setPrincipal(
                Boolean.TRUE.equals(dto.principal())
            );

            person.addAddress(address);
        });
    }

    private void copyDtoToSupplier(
        SupplierRequestDto dto,
        Supplier supplier
    ) {

        supplier.setCondicaoPagamentoPadrao(
            dto.condicaoPagamentoPadrao()
        );

        supplier.setPrazoEntregaDias(
            dto.prazoEntregaDias()
        );

        supplier.setValorMinimoPedido(
            dto.valorMinimoPedido()
        );

        supplier.setCategoria(
            dto.categoria()
        );

        supplier.setObservacoesComerciais(
            dto.observacoesComerciais()
        );

        if (dto.bankDetails() != null) {

            BankDetails bank = new BankDetails(
                dto.bankDetails().banco(),
                dto.bankDetails().agencia(),
                dto.bankDetails().conta(),
                parseTipoConta(
                    dto.bankDetails().tipoConta()
                ),
                dto.bankDetails().chavePix()
            );

            supplier.setBankDetails(bank);
        }

        if (dto.contatos() != null) {

            supplier.getContatos().clear();

            dto.contatos().forEach(c ->
                supplier.getContatos().add(
                    new SupplierContact(
                        c.nome(),
                        c.cargo(),
                        c.email(),
                        c.telefone(),
                        c.setor()
                    )
                )
            );
        }

        if (dto.documentos() != null) {

            supplier.getDocumentos().clear();

            dto.documentos().forEach(d ->
                supplier.getDocumentos().add(
                    new SupplierDocument(
                        d.tipoDocumento(),
                        d.numeroOuUrl(),
                        d.dataValidade()
                    )
                )
            );
        }
    }

    private ContactType parseContactType(String value) {

        if (value == null || value.isBlank()) {
            throw new BusinessException(
                "O tipo de contato deve ser informado."
            );
        }

        try {
            return ContactType.valueOf(
                value.trim().toUpperCase()
            );
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                "Tipo de contato inválido: " + value
            );
        }
    }

    private AddressType parseAddressType(String value) {

        if (value == null || value.isBlank()) {
            throw new BusinessException(
                "O tipo de endereço deve ser informado."
            );
        }

        try {
            return AddressType.valueOf(
                value.trim().toUpperCase()
            );
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                "Tipo de endereço inválido: " + value
            );
        }
    }

    private TipoContaBancaria parseTipoConta(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return TipoContaBancaria.valueOf(
                value.trim().toUpperCase()
            );
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                "Tipo de conta bancária inválido: " + value
            );
        }
    }
}