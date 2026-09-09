package net.ddns.lexdev.systempro_api.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.dto.ChangePasswordDto;
import net.ddns.lexdev.systempro_api.dto.UserCreateDto;
import net.ddns.lexdev.systempro_api.dto.UserResponseDto;
import net.ddns.lexdev.systempro_api.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponseDto register(UserCreateDto dto) {
        User user = new User();
        user.setUsername(dto.username());
        user.setEmail(dto.email());
        user.setPassword(passwordEncoder.encode(dto.password()));

        User savedUser = userRepository.save(user);
        return new UserResponseDto(savedUser);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordDto dto) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + username));

        // 1. Valida se a senha atual está correta
        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new BadCredentialsException("A senha atual informada está incorreta.");
        }

        // 2. Impede reuso imediato da mesma senha (boa prática de segurança)
        if (passwordEncoder.matches(dto.newPassword(), user.getPassword())) {
            throw new IllegalArgumentException("A nova senha não pode ser igual à senha atual.");
        }

        // 3. Codifica e atualiza a nova senha
        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);
    }
}