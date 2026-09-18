package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import net.ddns.lexdev.systempro_api.domain.PersonAddress;

public class PersonAddressTest {

    @Test
    void deveNormalizarCepRemovendoCaracteresNaoNumericos() {
        PersonAddress address = new PersonAddress();

        address.setCep("36.200-000");

        ReflectionTestUtils.invokeMethod(address, "normalizeFields");

        assertThat(address.getCep())
            .isEqualTo("36200000");
    }

    @Test
    void deveNormalizarUfRemovendoEspacosEConvertendoParaMaiusculas() {
        PersonAddress address = new PersonAddress();

        address.setUf(" mg ");

        ReflectionTestUtils.invokeMethod(address, "normalizeFields");

        assertThat(address.getUf())
            .isEqualTo("MG");
    }

    @Test
    void deveNormalizarCepEUfAoMesmoTempo() {
        PersonAddress address = new PersonAddress();

        address.setCep("36.200-000");
        address.setUf(" mg ");

        ReflectionTestUtils.invokeMethod(address, "normalizeFields");

        assertThat(address.getCep())
            .isEqualTo("36200000");

        assertThat(address.getUf())
            .isEqualTo("MG");
    }

    @Test
    void naoDeveFalharQuandoCepForNulo() {
        PersonAddress address = new PersonAddress();

        address.setCep(null);
        address.setUf("MG");

        ReflectionTestUtils.invokeMethod(address, "normalizeFields");

        assertThat(address.getCep()).isNull();
        assertThat(address.getUf()).isEqualTo("MG");
    }

    @Test
    void naoDeveFalharQuandoUfForNulo() {
        PersonAddress address = new PersonAddress();

        address.setCep("36200000");
        address.setUf(null);

        ReflectionTestUtils.invokeMethod(address, "normalizeFields");

        assertThat(address.getCep()).isEqualTo("36200000");
        assertThat(address.getUf()).isNull();
    }
}