package net.ddns.lexdev.systempro_api.validator;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CpfCnpjValidatorTest {

    @Test
    @DisplayName("Deve validar CPF válido")
    void deveValidarCpfValido() {

        assertThat(
            CpfCnpjValidator.isValidCpf(
                "529.982.247-25"
            )
        )
            .isTrue();
    }

    @Test
    @DisplayName("Deve validar CPF válido sem máscara")
    void deveValidarCpfValidoSemMascara() {

        assertThat(
            CpfCnpjValidator.isValidCpf(
                "52998224725"
            )
        )
            .isTrue();
    }

    @Test
    @DisplayName("Deve rejeitar CPF com dígitos verificadores inválidos")
    void deveRejeitarCpfComDigitosVerificadoresInvalidos() {

        assertThat(
            CpfCnpjValidator.isValidCpf(
                "529.982.247-26"
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar CPF com quantidade inválida de dígitos")
    void deveRejeitarCpfComQuantidadeInvalidaDeDigitos() {

        assertThat(
            CpfCnpjValidator.isValidCpf(
                "5299822472"
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar CPF com todos os dígitos iguais")
    void deveRejeitarCpfComTodosOsDigitosIguais() {

        assertThat(
            CpfCnpjValidator.isValidCpf(
                "111.111.111-11"
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("Deve validar CNPJ válido")
    void deveValidarCnpjValido() {

        assertThat(
            CpfCnpjValidator.isValidCnpj(
                "11.222.333/0001-81"
            )
        )
            .isTrue();
    }

    @Test
    @DisplayName("Deve validar CNPJ válido sem máscara")
    void deveValidarCnpjValidoSemMascara() {

        assertThat(
            CpfCnpjValidator.isValidCnpj(
                "11222333000181"
            )
        )
            .isTrue();
    }

    @Test
    @DisplayName("Deve rejeitar CNPJ com dígitos verificadores inválidos")
    void deveRejeitarCnpjComDigitosVerificadoresInvalidos() {

        assertThat(
            CpfCnpjValidator.isValidCnpj(
                "11.222.333/0001-82"
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar CNPJ com quantidade inválida de dígitos")
    void deveRejeitarCnpjComQuantidadeInvalidaDeDigitos() {

        assertThat(
            CpfCnpjValidator.isValidCnpj(
                "1122233300018"
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar CNPJ com todos os dígitos iguais")
    void deveRejeitarCnpjComTodosOsDigitosIguais() {

        assertThat(
            CpfCnpjValidator.isValidCnpj(
                "11.111.111/1111-11"
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar CPF nulo")
    void deveRejeitarCpfNulo() {

        assertThat(
            CpfCnpjValidator.isValidCpf(null)
        )
            .isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar CNPJ nulo")
    void deveRejeitarCnpjNulo() {

        assertThat(
            CpfCnpjValidator.isValidCnpj(null)
        )
            .isFalse();
    }
}
