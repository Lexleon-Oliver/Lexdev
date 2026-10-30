package net.ddns.lexdev.systempro_api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveDtoLoggingTest {

    @Test
    void loginRequestMustRedactPasswordFromToString() {
        LoginRequest request = new LoginRequest("@support", "SenhaSuperSecreta123@");

        assertThat(request.toString())
            .contains("username=@support")
            .contains("password=[PROTECTED]")
            .doesNotContain("SenhaSuperSecreta123@");
    }

    @Test
    void changePasswordDtoMustRedactBothPasswordsFromToString() {
        ChangePasswordDto dto = new ChangePasswordDto(
            "SenhaAtualSuperSecreta123@",
            "NovaSenhaSuperSecreta456@"
        );

        assertThat(dto.toString())
            .contains("currentPassword=[PROTECTED]")
            .contains("newPassword=[PROTECTED]")
            .doesNotContain("SenhaAtualSuperSecreta123@")
            .doesNotContain("NovaSenhaSuperSecreta456@");
    }

    @Test
    void userCreateDtoMustRedactPasswordFromToString() {
        UserCreateDto dto = new UserCreateDto(
            "usuario",
            "Usuário",
            "Usuário de Teste",
            "usuario@example.com",
            "OutraSenhaSuperSecreta123@",
            "ROLE_USER"
        );

        assertThat(dto.toString())
            .contains("password=[PROTECTED]")
            .doesNotContain("OutraSenhaSuperSecreta123@");
    }
}
