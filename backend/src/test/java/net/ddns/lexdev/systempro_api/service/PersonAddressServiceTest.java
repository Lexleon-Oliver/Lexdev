package net.ddns.lexdev.systempro_api.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.PersonAddress;
import net.ddns.lexdev.systempro_api.dto.PersonAddressRequestDto;
import net.ddns.lexdev.systempro_api.enums.AddressType;
import net.ddns.lexdev.systempro_api.exception.BusinessException;

public class PersonAddressServiceTest {

    private PersonAddressService service;
    private Person person;

    @BeforeEach
    void setUp() {
        service = new PersonAddressService();
        person = new Person();
    }

    @Test
    void shouldPreserveExistingAddressesWhenAddressDtosIsNull() {
        PersonAddress existingAddress = createAddress(
            AddressType.RESIDENCIAL,
            "Rua Antiga",
            "123"
        );

        person.addAddress(existingAddress);

        service.updateAddresses(person, null);

        assertThat(person.getAddresses())
            .hasSize(1)
            .containsExactly(existingAddress);

        assertThat(existingAddress.getPerson())
            .isSameAs(person);
    }

    @Test
    void shouldRemoveAllAddressesWhenAddressDtosIsEmpty() {
        PersonAddress existingAddress = createAddress(
            AddressType.RESIDENCIAL,
            "Rua Antiga",
            "123"
        );

        person.addAddress(existingAddress);

        service.updateAddresses(person, List.of());

        assertThat(person.getAddresses()).isEmpty();

        assertThat(existingAddress.getPerson()).isNull();
    }

    @Test
    void shouldReplaceExistingAddressesWithNewAddresses() {
        PersonAddress oldAddress = createAddress(
            AddressType.RESIDENCIAL,
            "Rua Antiga",
            "123"
        );

        person.addAddress(oldAddress);

        PersonAddressRequestDto dto = new PersonAddressRequestDto(
            "COMERCIAL",
            "12345678",
            "Avenida Nova",
            "456",
            "Sala 10",
            "Centro",
            "Barbacena",
            "MG",
            true
        );

        service.updateAddresses(person, List.of(dto));

        assertThat(person.getAddresses()).hasSize(1);

        PersonAddress newAddress = person.getAddresses().get(0);

        assertThat(newAddress).isNotSameAs(oldAddress);

        assertThat(newAddress.getType())
            .isEqualTo(AddressType.COMERCIAL);

        assertThat(newAddress.getCep()).isEqualTo("12345678");
        assertThat(newAddress.getLogradouro()).isEqualTo("Avenida Nova");
        assertThat(newAddress.getNumero()).isEqualTo("456");
        assertThat(newAddress.getComplemento()).isEqualTo("Sala 10");
        assertThat(newAddress.getBairro()).isEqualTo("Centro");
        assertThat(newAddress.getCidade()).isEqualTo("Barbacena");
        assertThat(newAddress.getUf()).isEqualTo("MG");
        assertThat(newAddress.isPrincipal()).isTrue();

        assertThat(newAddress.getPerson()).isSameAs(person);
        assertThat(oldAddress.getPerson()).isNull();
    }

    @Test
    void shouldAddMultipleAddresses() {
        PersonAddressRequestDto residential = new PersonAddressRequestDto(
            "RESIDENCIAL",
            "36200000",
            "Rua A",
            "10",
            null,
            "Centro",
            "Barbacena",
            "MG",
            true
        );

        PersonAddressRequestDto commercial = new PersonAddressRequestDto(
            "COMERCIAL",
            "36200111",
            "Rua B",
            "20",
            "Sala 2",
            "São José",
            "Barbacena",
            "MG",
            false
        );

        service.updateAddresses(
            person,
            List.of(residential, commercial)
        );

        assertThat(person.getAddresses()).hasSize(2);

        assertThat(person.getAddresses())
            .extracting(PersonAddress::getType)
            .containsExactly(
                AddressType.RESIDENCIAL,
                AddressType.COMERCIAL
            );

        assertThat(person.getAddresses())
            .allSatisfy(address ->
                assertThat(address.getPerson()).isSameAs(person)
            );
    }

    @Test
    void shouldSetPrincipalToFalseWhenDtoPrincipalIsNull() {
        PersonAddressRequestDto dto = new PersonAddressRequestDto(
            "RESIDENCIAL",
            null,
            "Rua A",
            "10",
            null,
            null,
            "Barbacena",
            "MG",
            null
        );

        service.updateAddresses(person, List.of(dto));

        assertThat(person.getAddresses()).hasSize(1);

        assertThat(person.getAddresses().get(0).isPrincipal())
            .isFalse();
    }

    @Test
    void shouldParseAddressTypeIgnoringCaseAndWhitespace() {
        PersonAddressRequestDto dto = new PersonAddressRequestDto(
            "  residencial  ",
            null,
            "Rua A",
            "10",
            null,
            null,
            "Barbacena",
            "MG",
            false
        );

        service.updateAddresses(person, List.of(dto));

        assertThat(person.getAddresses())
            .hasSize(1);

        assertThat(person.getAddresses().get(0).getType())
            .isEqualTo(AddressType.RESIDENCIAL);
    }

    @Test
    void shouldThrowBusinessExceptionWhenAddressTypeIsNull() {
        PersonAddressRequestDto dto = new PersonAddressRequestDto(
            null,
            null,
            "Rua A",
            "10",
            null,
            null,
            "Barbacena",
            "MG",
            false
        );

        assertThatThrownBy(() ->
            service.updateAddresses(person, List.of(dto))
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage("O tipo de endereço deve ser informado.");
    }

    @Test
    void shouldThrowBusinessExceptionWhenAddressTypeIsBlank() {
        PersonAddressRequestDto dto = new PersonAddressRequestDto(
            "   ",
            null,
            "Rua A",
            "10",
            null,
            null,
            "Barbacena",
            "MG",
            false
        );

        assertThatThrownBy(() ->
            service.updateAddresses(person, List.of(dto))
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage("O tipo de endereço deve ser informado.");
    }

    @Test
    void shouldThrowBusinessExceptionWhenAddressTypeIsInvalid() {
        PersonAddressRequestDto dto = new PersonAddressRequestDto(
            "INVALIDO",
            null,
            "Rua A",
            "10",
            null,
            null,
            "Barbacena",
            "MG",
            false
        );

        assertThatThrownBy(() ->
            service.updateAddresses(person, List.of(dto))
        )
            .isInstanceOf(BusinessException.class)
            .hasMessage("Tipo de endereço inválido: INVALIDO");
    }

    private PersonAddress createAddress(
        AddressType type,
        String logradouro,
        String numero
    ) {
        PersonAddress address = new PersonAddress();

        address.setType(type);
        address.setLogradouro(logradouro);
        address.setNumero(numero);

        return address;
    }
}
