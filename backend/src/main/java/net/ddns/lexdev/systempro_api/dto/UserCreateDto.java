package net.ddns.lexdev.systempro_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateDto(
    @NotBlank @Size(min = 3, max = 50) String username,
    @NotBlank String name,
    @NotBlank String fullName,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 6) String password,
    @NotBlank String role
) {}
