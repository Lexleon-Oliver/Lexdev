package net.ddns.lexdev.systempro_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import net.ddns.lexdev.systempro_api.dto.ChangePasswordDto;
import net.ddns.lexdev.systempro_api.dto.UserResponseDto;
import net.ddns.lexdev.systempro_api.repository.UserRepository;
import net.ddns.lexdev.systempro_api.service.UserService;

@RestController
@RequestMapping("/users/me")
public class UserController {

    private final UserRepository userRepository;
    private final UserService userService;

    public UserController(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<UserResponseDto> getCurrentUser(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .map(user -> ResponseEntity.ok(new UserResponseDto(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordDto dto) {

        userService.changePassword(authentication.getName(), dto);
        return ResponseEntity.noContent().build(); // Retorna HTTP 204 No Content
    }
}
