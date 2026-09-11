package net.ddns.lexdev.systempro_api.dto;

import net.ddns.lexdev.systempro_api.domain.User;

public record UserResponseDto(
    Long id, 
    String username, 
    String email, 
    String fullName, 
    String role, 
    boolean active
) {
    public static UserResponseDto fromEntity(User user) {
        return new UserResponseDto(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getName(),
            user.getRole(),
            user.isActive()
        );
    }
}
