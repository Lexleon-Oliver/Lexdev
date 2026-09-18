package net.ddns.lexdev.systempro_api.dto;

import java.util.List;

import net.ddns.lexdev.systempro_api.domain.Client;
import net.ddns.lexdev.systempro_api.domain.Person;

public record ClientResponseDto(
    Long id,
    PersonResponseDto person,
    IndividualPersonResponseDto individual,
    LegalEntityResponseDto legalEntity,
    List<PersonContactResponseDto> contacts,
    List<PersonAddressResponseDto> addresses,
    Boolean active
) {

    public static ClientResponseDto fromEntity(Client client) {
        Person person = client.getPerson();

        List<PersonContactResponseDto> contacts =
            person.getContacts()
                .stream()
                .map(PersonContactResponseDto::fromEntity)
                .toList();

        List<PersonAddressResponseDto> addresses =
            person.getAddresses()
                .stream()
                .map(PersonAddressResponseDto::fromEntity)
                .toList();

        return new ClientResponseDto(
            client.getId(),
            PersonResponseDto.fromEntity(person),
            IndividualPersonResponseDto.fromEntity(
                person.getIndividualPerson()
            ),
            LegalEntityResponseDto.fromEntity(
                person.getLegalEntity()
            ),
            contacts,
            addresses,
            client.isActive()
        );
    }
}