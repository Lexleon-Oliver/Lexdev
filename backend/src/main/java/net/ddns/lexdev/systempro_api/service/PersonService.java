package net.ddns.lexdev.systempro_api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.config.CpfCnpjNormalizer;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.dto.PersonRequestDto;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.mapper.PersonMapper;
import net.ddns.lexdev.systempro_api.repository.PersonRepository;

@Service
public class PersonService {

    private final PersonRepository personRepository;
    private final PersonMapper personMapper;

    public PersonService(
        PersonRepository personRepository,
        PersonMapper personMapper
    ) {
        this.personRepository = personRepository;
        this.personMapper = personMapper;
    }

    /**
     * Obtém uma Person existente ou cria uma nova para o cadastro.
     */
    @Transactional
    public Person getOrCreateForRegistration(String cpfCnpj) {

        String cleanCpfCnpj = CpfCnpjNormalizer.normalize(cpfCnpj);

        return personRepository.findByCpfCnpj(cleanCpfCnpj)
            .orElseGet(() -> {

                if (personRepository
                    .findIncludingInactiveByCpfCnpj(cleanCpfCnpj)
                    .isPresent()) {

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
     * Cria uma nova Person a partir do DTO.
     */
    public Person createFromDto(PersonRequestDto dto) {
        return personMapper.toEntity(dto);
    }

    /**
     * Atualiza uma Person existente a partir do DTO.
     */
    public void updateFromDto(
        Person person,
        PersonRequestDto dto
    ) {
        ensureCpfCnpjAvailable(
            dto.cpfCnpj(),
            person.getId()
        );

        personMapper.updateEntity(person, dto);
    }

    /**
     * Verifica se o CPF/CNPJ pertence a outra Person.
     */
    @Transactional(readOnly = true)
    public void ensureCpfCnpjAvailable(
        String cpfCnpj,
        Long currentPersonId
    ) {

        String cleanCpfCnpj = CpfCnpjNormalizer.normalize(cpfCnpj);

        personRepository
            .findIncludingInactiveByCpfCnpj(cleanCpfCnpj)
            .ifPresent(existingPerson -> {

                if (!existingPerson
                    .getId()
                    .equals(currentPersonId)) {

                    throw new BusinessException(
                        "CPF/CNPJ já cadastrado para outra pessoa no sistema."
                    );
                }
            });
    }

    /**
     * Reativa uma Person inativa.
     */
    @Transactional
    public Person reactivate(Long personId) {

        Person person = personRepository
            .findIncludingInactiveById(personId)
            .orElseThrow(() ->
                new EntityNotFoundException(
                    "Pessoa não encontrada com o ID: " + personId
                )
            );

        if (!person.isActive()) {
            person.setActive(true);
        }

        return personRepository.save(person);
    }
}