package net.ddns.lexdev.systempro_api.service;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import net.ddns.lexdev.systempro_api.domain.Client;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.PersonAddress;
import net.ddns.lexdev.systempro_api.domain.PersonContact;
import net.ddns.lexdev.systempro_api.enums.AddressType;
import net.ddns.lexdev.systempro_api.enums.ContactType;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.integration.IntegrationTestBase;

@SpringBootTest
@ActiveProfiles("test")
class ClientPersistenceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void devePersistirClienteComPersonContatosEEndereco() {

        // ------------------------------------------------------------
        // Person
        // ------------------------------------------------------------

        Person person = new Person();

        person.setTipoPessoa(TipoPessoa.PF);
        person.setName("Maria da Silva");
        person.setCpfCnpj("123.456.789-00");

        // ------------------------------------------------------------
        // Contato de e-mail
        // ------------------------------------------------------------

        PersonContact email = new PersonContact();
        email.setType(ContactType.EMAIL);
        email.setValue("  MARIA@EXEMPLO.COM  ");
        email.setPrincipal(true);
        email.setDescription("E-mail principal");

        person.addContact(email);

        // ------------------------------------------------------------
        // Contato telefônico
        // ------------------------------------------------------------

        PersonContact telefone = new PersonContact();
        telefone.setType(ContactType.CELULAR);
        telefone.setValue("(31) 99999-8888");
        telefone.setPrincipal(false);
        telefone.setDescription("Celular");

        person.addContact(telefone);

        // ------------------------------------------------------------
        // Endereço
        // ------------------------------------------------------------

        PersonAddress address = new PersonAddress();

        address.setType(AddressType.RESIDENCIAL);
        address.setCep("36.400-000");
        address.setLogradouro("Rua dos Testes");
        address.setNumero("100");
        address.setComplemento("Apto 201");
        address.setBairro("Centro");
        address.setCidade("Barbacena");
        address.setUf(" mg ");
        address.setPrincipal(true);

        person.addAddress(address);

        // ------------------------------------------------------------
        // Client
        // ------------------------------------------------------------

        Client client = new Client(person);

        // ------------------------------------------------------------
        // Persistência do agregado
        //
        // Client -> cascade PERSIST -> Person
        // Person -> cascade ALL -> Contacts/Addresses
        // ------------------------------------------------------------

        entityManager.persist(client);

        entityManager.flush();

        assertThat(client.getId()).isNotNull();
        assertThat(person.getId()).isNotNull();
        assertThat(email.getId()).isNotNull();
        assertThat(telefone.getId()).isNotNull();
        assertThat(address.getId()).isNotNull();

        Long clientId = client.getId();
        Long personId = person.getId();

        // ------------------------------------------------------------
        // Limpa o primeiro nível de cache do Hibernate.
        //
        // A partir daqui, queremos verificar os dados novamente
        // a partir do banco.
        // ------------------------------------------------------------

        entityManager.clear();

        // ------------------------------------------------------------
        // Recarrega Client
        // ------------------------------------------------------------

        Client persistedClient =
            entityManager.find(Client.class, clientId);

        assertThat(persistedClient).isNotNull();
        assertThat(persistedClient.isActive()).isTrue();

        // ------------------------------------------------------------
        // Recarrega Person
        // ------------------------------------------------------------

        Person persistedPerson =
            entityManager.find(Person.class, personId);

        assertThat(persistedPerson).isNotNull();
        assertThat(persistedPerson.getName())
            .isEqualTo("Maria da Silva");

        // @PrePersist deve ter removido a máscara do CPF/CNPJ
        assertThat(persistedPerson.getCpfCnpj())
            .isEqualTo("12345678900");

        assertThat(persistedPerson.getTipoPessoa())
            .isEqualTo(TipoPessoa.PF);

        // ------------------------------------------------------------
        // Relacionamento Client -> Person
        // ------------------------------------------------------------

        assertThat(persistedClient.getPerson())
            .isNotNull();

        assertThat(persistedClient.getPerson().getId())
            .isEqualTo(personId);

        // ------------------------------------------------------------
        // Contacts
        // ------------------------------------------------------------

        assertThat(persistedPerson.getContacts())
            .hasSize(2);

        PersonContact persistedEmail =
            persistedPerson.getContacts()
                .stream()
                .filter(contact -> contact.getType() == ContactType.EMAIL)
                .findFirst()
                .orElseThrow();

        assertThat(persistedEmail.getValue())
            .isEqualTo("maria@exemplo.com");

        PersonContact persistedTelefone =
            persistedPerson.getContacts()
                .stream()
                .filter(contact -> contact.getType() == ContactType.CELULAR)
                .findFirst()
                .orElseThrow();

        assertThat(persistedTelefone.getValue())
            .isEqualTo("31999998888");

        // ------------------------------------------------------------
        // Addresses
        // ------------------------------------------------------------

        assertThat(persistedPerson.getAddresses())
            .hasSize(1);

        PersonAddress persistedAddress =
            persistedPerson.getAddresses().get(0);

        assertThat(persistedAddress.getCep())
            .isEqualTo("36400000");

        assertThat(persistedAddress.getUf())
            .isEqualTo("MG");

        assertThat(persistedAddress.getCidade())
            .isEqualTo("Barbacena");

        assertThat(persistedAddress.isPrincipal())
            .isTrue();

        // ------------------------------------------------------------
        // FK dos filhos
        // ------------------------------------------------------------

        assertThat(persistedEmail.getPerson().getId())
            .isEqualTo(personId);

        assertThat(persistedAddress.getPerson().getId())
            .isEqualTo(personId);
    }
}
