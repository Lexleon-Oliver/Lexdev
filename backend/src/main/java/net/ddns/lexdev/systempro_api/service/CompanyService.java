package net.ddns.lexdev.systempro_api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.config.CpfCnpjNormalizer;
import net.ddns.lexdev.systempro_api.domain.Company;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.dto.CompanyRequestDto;
import net.ddns.lexdev.systempro_api.dto.CompanyResponseDto;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.CompanyRepository;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final PersonService personService;
    private final PersonContactService personContactService;
    private final PersonAddressService personAddressService;

    public CompanyService(
        CompanyRepository companyRepository,
        PersonService personService,
        PersonContactService personContactService,
        PersonAddressService personAddressService
    ) {
        this.companyRepository = companyRepository;
        this.personService = personService;
        this.personContactService = personContactService;
        this.personAddressService = personAddressService;
    }

    @Transactional(readOnly = true)
    public CompanyResponseDto find() {
        return companyRepository.findFirstByOrderByIdAsc()
            .map(CompanyResponseDto::fromEntity)
            .orElseThrow(() -> new EntityNotFoundException(
                "A empresa proprietária do sistema ainda não foi cadastrada."
            ));
    }

    @Transactional
    public CompanyResponseDto save(CompanyRequestDto dto) {
        if (!TipoPessoa.PJ.name().equalsIgnoreCase(dto.person().tipoPessoa())) {
            throw new BusinessException(
                "A empresa proprietária do sistema deve ser uma pessoa jurídica."
            );
        }

        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        String cpfCnpj = CpfCnpjNormalizer.normalize(dto.person().cpfCnpj());

        if (company == null) {
            Person person = personService.getOrCreateForRegistration(cpfCnpj);
            personService.updateFromDto(
                person,
                dto.person(),
                null,
                dto.legalEntity()
            );
            personContactService.updateContacts(person, dto.contacts());
            personAddressService.updateAddresses(person, dto.addresses());

            Company newCompany = new Company(person);
            Company saved = companyRepository.save(newCompany);
            return CompanyResponseDto.fromEntity(saved);
        }

        Person person = company.getPerson();
        personService.updateFromDto(
            person,
            dto.person(),
            null,
            dto.legalEntity()
        );
        personContactService.updateContacts(person, dto.contacts());
        personAddressService.updateAddresses(person, dto.addresses());

        return CompanyResponseDto.fromEntity(companyRepository.save(company));
    }
}
