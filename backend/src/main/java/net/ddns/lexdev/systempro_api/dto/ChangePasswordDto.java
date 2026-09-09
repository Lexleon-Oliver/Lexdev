package net.ddns.lexdev.systempro_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordDto(
    @NotBlank(message = "A senha atual é obrigatória")
    String currentPassword,

    @NotBlank(message = "A nova senha é obrigatória")
    @Size(min = 6, message = "A nova senha deve ter no mínimo 6 caracteres")
    String newPassword
) {}
