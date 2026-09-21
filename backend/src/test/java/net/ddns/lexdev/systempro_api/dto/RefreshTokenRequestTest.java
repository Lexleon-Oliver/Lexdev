package net.ddns.lexdev.systempro_api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class RefreshTokenRequestTest {

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
    @DisplayName("Deve aceitar refresh token válido")
    void deveAceitarRefreshTokenValido() {
        RefreshTokenRequest request =
                new RefreshTokenRequest("refresh-token");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("Deve rejeitar refresh token vazio")
    void deveRejeitarRefreshTokenVazio() {
        RefreshTokenRequest request =
                new RefreshTokenRequest("");

        assertThat(validator.validate(request))
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("refreshToken");
                    assertThat(violation.getMessage())
                            .isEqualTo("O refresh token é obrigatório");
                });
    }

    @Test
    @DisplayName("Deve rejeitar refresh token nulo")
    void deveRejeitarRefreshTokenNulo() {
        RefreshTokenRequest request =
                new RefreshTokenRequest(null);

        assertThat(validator.validate(request))
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString())
                                .isEqualTo("refreshToken")
                );
    }

    @Test
    @DisplayName("Deve rejeitar refresh token contendo apenas espaços")
    void deveRejeitarRefreshTokenComEspacos() {
        RefreshTokenRequest request =
                new RefreshTokenRequest("   ");

        assertThat(validator.validate(request))
                .anySatisfy(violation ->
                        assertThat(violation.getMessage())
                                .isEqualTo("O refresh token é obrigatório")
                );
    }
}
