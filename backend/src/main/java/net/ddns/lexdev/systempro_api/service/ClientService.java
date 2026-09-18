package net.ddns.lexdev.systempro_api.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.config.CpfCnpjNormalizer;
import net.ddns.lexdev.systempro_api.domain.Client;
import net.ddns.lexdev.systempro_api.domain.IndividualPerson;
import net.ddns.lexdev.systempro_api.domain.LegalEntity;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.PersonAddress;
import net.ddns.lexdev.systempro_api.domain.PersonContact;
import net.ddns.lexdev.systempro_api.dto.ClientRequestDto;
import net.ddns.lexdev.systempro_api.dto.ClientResponseDto;
import net.ddns.lexdev.systempro_api.dto.IndividualPersonRequestDto;
import net.ddns.lexdev.systempro_api.dto.LegalEntityRequestDto;
import net.ddns.lexdev.systempro_api.dto.PersonAddressRequestDto;
import net.ddns.lexdev.systempro_api.dto.PersonContactRequestDto;
import net.ddns.lexdev.systempro_api.enums.AddressType;
import net.ddns.lexdev.systempro_api.enums.ContactType;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.ClientRepository;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final PersonService personService;

    public ClientService(
        ClientRepository clientRepository,
        PersonService personService
    ) {
        this.clientRepository = clientRepository;
        this.personService = personService;
    }

    @Transactional(readOnly = true)
    public Page<ClientResponseDto> findAll(Pageable pageable) {
        return clientRepository.findAllWithPerson(pageable)
            .map(ClientResponseDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public ClientResponseDto findById(Long id) {
        Client client = clientRepository.findByIdWithPerson(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Cliente não encontrado com o ID: " + id
            ));

        return ClientResponseDto.fromEntity(client);
    }

    @Transactional
    public ClientResponseDto create(ClientRequestDto dto) {

        String cpfCnpj = CpfCnpjNormalizer.normalize(
            dto.person().cpfCnpj()
        );

        if (clientRepository.existsByPersonCpfCnpj(cpfCnpj)) {
            throw new BusinessException(
                "Esta pessoa/empresa já está cadastrada como cliente ativo."
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

        Client client = new Client();
        client.setPerson(person);

        if (dto.active() != null) {
            client.setActive(dto.active());
        }

        return ClientResponseDto.fromEntity(
            clientRepository.save(client)
        );
    }

    @Transactional
    public ClientResponseDto update(
        Long id,
        ClientRequestDto dto
    ) {

        Client client = clientRepository.findByIdWithPerson(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Cliente não encontrado com o ID: " + id
            ));

        Person person = client.getPerson();

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

        if (dto.active() != null) {
            client.setActive(dto.active());
        }

        return ClientResponseDto.fromEntity(
            clientRepository.save(client)
        );
    }

    @Transactional
    public void delete(Long id) {

        Client client = clientRepository.findByIdWithPerson(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Cliente não encontrado com o ID: " + id
            ));

        client.setActive(false);

        clientRepository.save(client);
    }

    private void applyPersonSpecialization(
        Person person,
        ClientRequestDto dto
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
}