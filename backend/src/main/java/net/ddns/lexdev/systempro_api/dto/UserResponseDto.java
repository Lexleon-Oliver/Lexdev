package net.ddns.lexdev.systempro_api.dto;

import net.ddns.lexdev.systempro_api.domain.User;

public record UserResponseDto(Long id, String username, String role, boolean active) {
    public UserResponseDto(User user) {
        this(user.getId(), user.getUsername(), user.getRole(), user.isActive());
    }
}
