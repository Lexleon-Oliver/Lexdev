package net.ddns.lexdev.systempro_api.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.PersonContact;
import net.ddns.lexdev.systempro_api.dto.PersonContactRequestDto;
import net.ddns.lexdev.systempro_api.enums.ContactType;
import net.ddns.lexdev.systempro_api.exception.BusinessException;

class PersonContactServiceTest {

    private PersonContactService service;
    private Person person;

    @BeforeEach
    void setUp() {
        service = new PersonContactService();
        person = new Person();
    }

    @Test
    void shouldPreserveExistingContactsWhenContactDtosIsNull() {
        PersonContact existingContact = createContact(
            ContactType.EMAIL,
            "teste@email.com",
            true
        );

        person.addContact(existingContact);

        service.updateContacts(person, null);

        assertThat(person.getContacts())
            .hasSize(1)
            .containsExactly(existingContact);

        assertThat(existingContact.getPerson()).isSameAs(person);
    }

    @Test
    void shouldRemoveAllContactsWhenContactDtosIsEmpty() {
        PersonContact existingContact = createContact(
            ContactType.EMAIL,
            "teste@email.com",
            true
        );

        person.addContact(existingContact);

        service.updateContacts(person, List.of());

        assertThat(person.getContacts()).isEmpty();
        assertThat(existingContact.getPerson()).isNull();
    }

    @Test
    void shouldReplaceExistingContactsWithNewContacts() {
        PersonContact oldContact = createContact(
            ContactType.EMAIL,
            "antigo@email.com",
            true
        );

        person.addContact(oldContact);

        PersonContactRequestDto dto = new PersonContactRequestDto(
            "EMAIL",
            "novo@email.com",
            true,
            "Contato principal"
        );

        service.updateContacts(person, List.of(dto));

        assertThat(person.getContacts()).hasSize(1);

        PersonContact newContact = person.getContacts().get(0);

        assertThat(newContact)
            .isNotSameAs(oldContact);

        assertThat(newContact.getType())
            .isEqualTo(ContactType.EMAIL);

        assertThat(newContact.getValue())
            .isEqualTo("novo@email.com");

        assertThat(newContact.isPrincipal())
            .isTrue();

        assertThat(newContact.getDescription())
            .isEqualTo("Contato principal");

        assertThat(newContact.getPerson())
            .isSameAs(person);

        assertThat(oldContact.getPerson())
            .isNull();
    }

    @Test
    void shouldAddMultipleContacts() {
        PersonContactRequestDto email = new PersonContactRequestDto(
            "EMAIL",
            "teste@email.com",
            true,
            "E-mail principal"
        );

        PersonContactRequestDto phone = new PersonContactRequestDto(
            "TELEFONE",
            "3333333333",
            false,
            "Telefone comercial"
        );

        service.updateContacts(person, List.of(email, phone));

        assertThat(person.getContacts()).hasSize(2);

        assertThat(person.getContacts())
            .extracting(PersonContact::getType)
            .containsExactly(
                ContactType.EMAIL,
                ContactType.TELEFONE
            );

        assertThat(person.getContacts())
            .allSatisfy(contact ->
                assertThat(contact.getPerson()).isSameAs(person)
            );
    }

    @Test
    void shouldSetPrincipalToFalseWhenDtoPrincipalIsNull() {
        PersonContactRequestDto dto = new PersonContactRequestDto(
            "EMAIL",
            "teste@email.com",
            null,
            null
        );

        service.updateContacts(person, List.of(dto));

        assertThat(person.getContacts())
            .hasSize(1);

        PersonContact contact = person.getContacts().get(0);

        assertThat(contact.isPrincipal()).isFalse();
        assertThat(contact.getDescription()).isNull();
    }

    @Test
    void shouldParseContactTypeIgnoringCaseAndWhitespace() {
        PersonContactRequestDto dto = new PersonContactRequestDto(
            "  email  ",
            "teste@email.com",
            false,
            null
        );

        service.updateContacts(person, List.of(dto));

        assertThat(person.getContacts())
            .hasSize(1);

        assertThat(person.getContacts().get(0).getType())
            .isEqualTo(ContactType.EMAIL);
    }

    @Test
    void shouldThrowBusinessExceptionWhenContactTypeIsNull() {
        PersonContactRequestDto dto = new PersonContactRequestDto(
            null,
            "teste@email.com",
            false,
            null
        );

        assertThatThrownBy(() ->
            service.updateContacts(person, List.of(dto))
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage("O tipo de contato deve ser informado.");
    }

    @Test
    void shouldThrowBusinessExceptionWhenContactTypeIsBlank() {
        PersonContactRequestDto dto = new PersonContactRequestDto(
            "   ",
            "teste@email.com",
            false,
            null
        );

        assertThatThrownBy(() ->
            service.updateContacts(person, List.of(dto))
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage("O tipo de contato deve ser informado.");
    }

    @Test
    void shouldThrowBusinessExceptionWhenContactTypeIsInvalid() {
        PersonContactRequestDto dto = new PersonContactRequestDto(
            "INVALIDO",
            "teste@email.com",
            false,
            null
        );

        assertThatThrownBy(() ->
            service.updateContacts(person, List.of(dto))
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage("Tipo de contato inválido: INVALIDO");
    }

    private PersonContact createContact(
        ContactType type,
        String value,
        boolean principal
    ) {
        PersonContact contact = new PersonContact();

        contact.setType(type);
        contact.setValue(value);
        contact.setPrincipal(principal);

        return contact;
    }
}
