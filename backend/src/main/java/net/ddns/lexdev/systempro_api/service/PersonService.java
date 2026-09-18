package net.ddns.lexdev.systempro_api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.config.CpfCnpjNormalizer;
import net.ddns.lexdev.systempro_api.domain.IndividualPerson;
import net.ddns.lexdev.systempro_api.domain.LegalEntity;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.TipoPessoaParser;
import net.ddns.lexdev.systempro_api.dto.IndividualPersonRequestDto;
import net.ddns.lexdev.systempro_api.dto.LegalEntityRequestDto;
import net.ddns.lexdev.systempro_api.dto.PersonRequestDto;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.mapper.IndividualPersonMapper;
import net.ddns.lexdev.systempro_api.mapper.LegalEntityMapper;
import net.ddns.lexdev.systempro_api.mapper.PersonMapper;
import net.ddns.lexdev.systempro_api.repository.PersonRepository;

@Service
public class PersonService {

    private final PersonRepository personRepository;
    private final PersonMapper personMapper;
    private final IndividualPersonMapper individualPersonMapper;
    private final LegalEntityMapper legalEntityMapper;

    public PersonService(
        PersonRepository personRepository,
        PersonMapper personMapper,
        IndividualPersonMapper individualPersonMapper,
        LegalEntityMapper legalEntityMapper
    ) {
        this.personRepository = personRepository;
        this.personMapper = personMapper;
        this.individualPersonMapper = individualPersonMapper;
        this.legalEntityMapper = legalEntityMapper;
    }

    /**
     * Obtém uma Person existente ou cria uma nova para o cadastro.
     */
    @Transactional
    public Person getOrCreateForRegistration(String cpfCnpj) {

        String cleanCpfCnpj =
            CpfCnpjNormalizer.normalize(cpfCnpj);

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
     * Cria uma Person completa a partir dos dados básicos
     * e da especialização PF ou PJ.
     *
     * PF:
     * - cria IndividualPerson;
     *
     * PJ:
     * - cria LegalEntity.
     */
    @Transactional
    public Person createFromDto(
        PersonRequestDto personDto,
        IndividualPersonRequestDto individualDto,
        LegalEntityRequestDto legalEntityDto
    ) {

        TipoPessoa tipoPessoa =
            TipoPessoaParser.parse(personDto.tipoPessoa());

        validateStructure(
            tipoPessoa,
            individualDto,
            legalEntityDto
        );

        Person person =
            personMapper.toEntity(personDto);

        if (tipoPessoa == TipoPessoa.PF) {

            IndividualPerson individual =
                individualPersonMapper.toEntity(
                    individualDto,
                    person
                );

            person.setIndividualPerson(individual);

        } else {

            LegalEntity legalEntity =
                legalEntityMapper.toEntity(
                    legalEntityDto,
                    person
                );

            person.setLegalEntity(legalEntity);
        }

        return person;
    }

    /**
     * Valida a estrutura específica da Person
     * conforme o tipo PF ou PJ.
     */
    private void validateStructure(
        TipoPessoa tipoPessoa,
        IndividualPersonRequestDto individualDto,
        LegalEntityRequestDto legalEntityDto
    ) {

        if (tipoPessoa == TipoPessoa.PF) {

            if (individualDto == null) {
                throw new BusinessException(
                    "Pessoa Física deve possuir dados específicos de Pessoa Física."
                );
            }

            if (legalEntityDto != null) {
                throw new BusinessException(
                    "Pessoa Física não pode possuir dados de Pessoa Jurídica."
                );
            }

            return;
        }

        if (tipoPessoa == TipoPessoa.PJ) {

            if (legalEntityDto == null) {
                throw new BusinessException(
                    "Pessoa Jurídica deve possuir dados específicos de Pessoa Jurídica."
                );
            }

            if (individualDto != null) {
                throw new BusinessException(
                    "Pessoa Jurídica não pode possuir dados de Pessoa Física."
                );
            }
        }
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

        personMapper.updateEntity(
            person,
            dto
        );
    }

    /**
     * Verifica se o CPF/CNPJ pertence a outra Person.
     */
    @Transactional(readOnly = true)
    public void ensureCpfCnpjAvailable(
        String cpfCnpj,
        Long currentPersonId
    ) {

        String cleanCpfCnpj =
            CpfCnpjNormalizer.normalize(cpfCnpj);

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