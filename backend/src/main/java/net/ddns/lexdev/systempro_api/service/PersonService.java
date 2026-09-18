package net.ddns.lexdev.systempro_api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.dto.PersonRequestDto;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.PersonRepository;

@Service
public class PersonService {

    private final PersonRepository personRepository;

    public PersonService(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    /**
     * Obtém uma Person existente ou cria uma nova para o cadastro.
     */
    @Transactional
    public Person getOrCreateForRegistration(String cpfCnpj) {
        String cleanCpfCnpj = sanitize(cpfCnpj);

        return personRepository.findByCpfCnpj(cleanCpfCnpj)
            .orElseGet(() -> {
                if (personRepository.findIncludingInactiveByCpfCnpj(cleanCpfCnpj).isPresent()) {
                    throw new BusinessException(
                        "Já existe um cadastro inativado para este CPF/CNPJ. " +
                        "Solicite a reativação ao Suporte."
                    );
                }

                Person person = new Person();
                person.setCpfCnpj(cleanCpfCnpj);
                return person;
            });
    }

    /**
     * Copia as informações do PersonRequestDto para a entidade Person.
     */
    public void copyDtoToPerson(PersonRequestDto dto, Person person) {
        if (dto == null) return;

        person.setTipoPessoa(parseTipoPessoa(dto.tipoPessoa()));
        person.setName(dto.name());
        person.setNomeFantasia(dto.nomeFantasia());
        person.setRgIe(dto.rgIe());
        person.setEmail(dto.email());
        person.setPhone(dto.phone());
        person.setCep(dto.cep());
        person.setLogradouro(dto.logradouro());
        person.setNumero(dto.numero());
        person.setComplemento(dto.complemento());
        person.setBairro(dto.bairro());
        person.setCidade(dto.cidade());
        person.setUf(dto.uf());
        person.setCpfCnpj(dto.cleanCpfCnpj());
    }

    /**
     * Verifica se o CPF/CNPJ pertence a outra Person.
     */
    @Transactional(readOnly = true)
    public void ensureCpfCnpjAvailable(String cpfCnpj, Long currentPersonId) {
        String cleanCpfCnpj = sanitize(cpfCnpj);

        personRepository.findIncludingInactiveByCpfCnpj(cleanCpfCnpj)
            .ifPresent(existingPerson -> {
                if (!existingPerson.getId().equals(currentPersonId)) {
                    throw new BusinessException(
                        "CPF/CNPJ já cadastrado para outra pessoa no sistema."
                    );
                }
            });
    }

    @Transactional
    public Person reactivate(Long personId) {
        Person person = personRepository.findIncludingInactiveById(personId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Pessoa não encontrada com o ID: " + personId
            ));

        if (!person.isActive()) {
            person.setActive(true);
        }

        return personRepository.save(person);
    }

    public String sanitize(String value) {
        return value != null ? value.replaceAll("\\D", "") : null;
    }

    private TipoPessoa parseTipoPessoa(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return TipoPessoa.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Tipo de pessoa inválido: " + value);
        }
    }
}