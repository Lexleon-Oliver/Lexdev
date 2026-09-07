package net.ddns.lexdev.systempro_api.dto;

public record AuthResponse(
    String token,
    String tokenType
) {
    public AuthResponse(String token) {
        this(token, "Bearer");
    }
}