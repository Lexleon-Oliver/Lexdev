package net.ddns.lexdev.systempro_api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class LoginRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("Deve aceitar login válido")
    void deveAceitarLoginValido() {
        LoginRequest request = new LoginRequest("admin", "123456");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("Deve rejeitar username vazio")
    void deveRejeitarUsernameVazio() {
        LoginRequest request = new LoginRequest("", "123456");

        assertThat(validator.validate(request))
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("username");
                    assertThat(violation.getMessage())
                            .isEqualTo("O usuário é obrigatório");
                });
    }

    @Test
    @DisplayName("Deve rejeitar username nulo")
    void deveRejeitarUsernameNulo() {
        LoginRequest request = new LoginRequest(null, "123456");

        assertThat(validator.validate(request))
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString())
                                .isEqualTo("username")
                );
    }

    @Test
    @DisplayName("Deve rejeitar senha vazia")
    void deveRejeitarSenhaVazia() {
        LoginRequest request = new LoginRequest("admin", "");

        assertThat(validator.validate(request))
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("password");
                    assertThat(violation.getMessage())
                            .isEqualTo("A senha é obrigatória");
                });
    }

    @Test
    @DisplayName("Deve rejeitar senha nula")
    void deveRejeitarSenhaNula() {
        LoginRequest request = new LoginRequest("admin", null);

        assertThat(validator.validate(request))
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString())
                                .isEqualTo("password")
                );
    }
}