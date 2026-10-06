package net.ddns.lexdev.systempro_api.dto;

import java.util.List;

import net.ddns.lexdev.systempro_api.domain.Company;
import net.ddns.lexdev.systempro_api.domain.Person;

public record CompanyResponseDto(
    Long id,
    PersonResponseDto person,
    LegalEntityResponseDto legalEntity,
    List<PersonContactResponseDto> contacts,
    List<PersonAddressResponseDto> addresses
) {

    public static CompanyResponseDto fromEntity(Company company) {
        Person person = company.getPerson();

        return new CompanyResponseDto(
            company.getId(),
            PersonResponseDto.fromEntity(person),
            LegalEntityResponseDto.fromEntity(person.getLegalEntity()),
            person.getContacts().stream().map(PersonContactResponseDto::fromEntity).toList(),
            person.getAddresses().stream().map(PersonAddressResponseDto::fromEntity).toList()
        );
    }
}
