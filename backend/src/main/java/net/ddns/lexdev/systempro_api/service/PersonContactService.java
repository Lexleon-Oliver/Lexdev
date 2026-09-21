package net.ddns.lexdev.systempro_api.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.PersonContact;
import net.ddns.lexdev.systempro_api.dto.PersonContactRequestDto;
import net.ddns.lexdev.systempro_api.enums.ContactType;
import net.ddns.lexdev.systempro_api.exception.BusinessException;

@Service
public class PersonContactService {

    /**
     * Atualiza os contatos de uma Person.
     *
     * null: preserva os contatos existentes.
     * lista vazia: remove todos os contatos.
     * lista preenchida: substitui os contatos existentes.
     */
    public void updateContacts(
        Person person,
        List<PersonContactRequestDto> contactDtos
    ) {

        if (contactDtos == null) {
            return;
        }

        // Remove os contatos atuais mantendo
        // o relacionamento bidirecional consistente.
        for (PersonContact contact :
                new ArrayList<>(person.getContacts())) {

            person.removeContact(contact);
        }

        // Lista vazia significa remover todos.
        if (contactDtos.isEmpty()) {
            return;
        }

        for (PersonContactRequestDto dto : contactDtos) {

            PersonContact contact = new PersonContact();

            contact.setType(parseContactType(dto.type()));
            contact.setValue(dto.value());
            contact.setPrincipal(
                Boolean.TRUE.equals(dto.principal())
            );
            contact.setDescription(dto.description());

            person.addContact(contact);
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
}
