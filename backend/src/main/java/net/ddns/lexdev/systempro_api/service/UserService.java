package net.ddns.lexdev.systempro_api.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.dto.ChangePasswordDto;
import net.ddns.lexdev.systempro_api.dto.UserCreateDto;
import net.ddns.lexdev.systempro_api.dto.UserResponseDto;
import net.ddns.lexdev.systempro_api.dto.UserUpdateDto;
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
        if (userRepository.existsByUsername(dto.username())) {
            throw new IllegalArgumentException("Nome de usuário já existe.");
        }
        if (userRepository.existsByEmail(dto.email())) {
            throw new IllegalArgumentException("Email já existe.");
        }
        User user = new User();
        user.setUsername(dto.username());
        user.setName(dto.name());
        user.setEmail(dto.email());
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setRole(dto.role());
        User savedUser = userRepository.save(user);
        return new UserResponseDto(savedUser);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordDto dto) {
        User user = userRepository.findByUsernameAndActiveTrue(username)
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

    @Transactional(readOnly = true)
    public Page<UserResponseDto> findAll(Pageable pageable) {
        return userRepository.findByActiveTrue(pageable).map(UserResponseDto::new);
    }

    @Transactional(readOnly = true)
    public UserResponseDto findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + id));
        return new UserResponseDto(user);
    }

    @Transactional(readOnly = true)
    public UserResponseDto findByUsername(String username) {
        User user = userRepository.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + username));
        return new UserResponseDto(user);
    }

    @Transactional
    public UserResponseDto update(Long id, UserUpdateDto dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + id));

        user.setName(dto.name());
        user.setEmail(dto.email());
        user.setRole(dto.role());
        user.setActive(dto.active());

        return new UserResponseDto(userRepository.save(user));
    }

    @Transactional
    public void delete(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + id));

        user.setActive(false);
        userRepository.save(user);
    }
}