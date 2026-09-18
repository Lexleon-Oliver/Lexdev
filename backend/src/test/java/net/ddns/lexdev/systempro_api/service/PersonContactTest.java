package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.lexdev.systempro_api.domain.PersonContact;
import net.ddns.lexdev.systempro_api.enums.ContactType;

public class PersonContactTest {

    @Test
    void deveNormalizarEmailParaMinusculasERemoverEspacos() {
        PersonContact contact = new PersonContact();

        contact.setType(ContactType.EMAIL);
        contact.setValue("  Joao.Silva@EMAIL.COM  ");

        ReflectionTestUtils.invokeMethod(contact, "normalizeFields");

        assertThat(contact.getValue())
            .isEqualTo("joao.silva@email.com");
    }

    @Test
    void deveRemoverCaracteresNaoNumericosDoTelefone() {
        PersonContact contact = new PersonContact();

        contact.setType(ContactType.TELEFONE);
        contact.setValue("(32) 3333-4444");

        ReflectionTestUtils.invokeMethod(contact, "normalizeFields");

        assertThat(contact.getValue())
            .isEqualTo("3233334444");
    }

    @Test
    void deveRemoverCaracteresNaoNumericosDoCelular() {
        PersonContact contact = new PersonContact();

        contact.setType(ContactType.CELULAR);
        contact.setValue("(32) 9 9999-8888");

        ReflectionTestUtils.invokeMethod(contact, "normalizeFields");

        assertThat(contact.getValue())
            .isEqualTo("32999998888");
    }

    @Test
    void deveRemoverCaracteresNaoNumericosDoWhatsapp() {
        PersonContact contact = new PersonContact();

        contact.setType(ContactType.WHATSAPP);
        contact.setValue("+55 (32) 9 9999-7777");

        ReflectionTestUtils.invokeMethod(contact, "normalizeFields");

        assertThat(contact.getValue())
            .isEqualTo("5532999997777");
    }

    @Test
    void naoDeveFalharQuandoValorForNulo() {
        PersonContact contact = new PersonContact();

        contact.setType(ContactType.EMAIL);
        contact.setValue(null);

        ReflectionTestUtils.invokeMethod(contact, "normalizeFields");

        assertThat(contact.getValue()).isNull();
    }
}