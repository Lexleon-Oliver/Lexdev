package net.ddns.lexdev.systempro_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserUpdateDto(
    @NotBlank String name,
    @NotBlank @Email String email,
    @NotBlank String role,
    boolean active
) {}
