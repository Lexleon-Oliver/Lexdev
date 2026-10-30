package net.ddns.lexdev.systempro_api.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.dto.ChangePasswordDto;
import net.ddns.lexdev.systempro_api.dto.UserCreateDto;
import net.ddns.lexdev.systempro_api.dto.UserResponseDto;
import net.ddns.lexdev.systempro_api.dto.UserUpdateDto;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.UserRepository;

@Service
public class UserService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_SUPPORT = "ROLE_SUPPORT";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponseDto register(UserCreateDto dto, Authentication authentication) {
        assertCanManageUsers(authentication);
        assertCanAssignRole(authentication, dto.role());

        if (userRepository.existsByUsername(dto.username())) {
            throw new BusinessException("Nome de usuário já existe.");
        }
        if (userRepository.existsByEmail(dto.email())) {
            throw new BusinessException("Email já existe.");
        }

        User user = new User();
        user.setUsername(dto.username());
        user.setName(dto.name());
        user.setEmail(dto.email());
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setRole(dto.role());

        return UserResponseDto.fromEntity(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Page<UserResponseDto> findAll(Pageable pageable, String query) {
        String term = query == null ? "" : query.trim().toLowerCase();
        return userRepository.search(term, pageable).map(UserResponseDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public UserResponseDto findById(Long id) {
        User user = userRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + id));
        return UserResponseDto.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public UserResponseDto findByUsername(String username) {
        User user = userRepository.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + username));
        return UserResponseDto.fromEntity(user);
    }

    @Transactional
    public UserResponseDto update(Long id, UserUpdateDto dto, Authentication authentication) {
        assertCanManageUsers(authentication);

        User user = userRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + id));

        assertCanManageTarget(authentication, user);
        assertCanAssignRole(authentication, dto.role());

        user.setName(dto.name());
        user.setEmail(dto.email());
        user.setRole(dto.role());

        return UserResponseDto.fromEntity(userRepository.save(user));
    }

    @Transactional
    public void changePassword(String username, ChangePasswordDto dto) {
        User user = userRepository.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + username));

        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new BadCredentialsException("A senha atual informada está incorreta.");
        }

        if (passwordEncoder.matches(dto.newPassword(), user.getPassword())) {
            throw new BusinessException("A nova senha não pode ser igual à senha atual.");
        }

        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void delete(Long id, Authentication authentication) {
        assertCanManageUsers(authentication);

        User user = userRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + id));

        assertCanManageTarget(authentication, user);

        user.setActive(false);
        userRepository.save(user);
    }

    private void assertCanManageUsers(Authentication authentication) {
        if (!hasRole(authentication, ROLE_ADMIN) && !hasRole(authentication, ROLE_SUPPORT)) {
            throw new AccessDeniedException("Somente administradores ou suporte podem gerenciar usuários.");
        }
    }

    private void assertCanAssignRole(Authentication authentication, String requestedRole) {
        if (ROLE_SUPPORT.equals(requestedRole) && !hasRole(authentication, ROLE_SUPPORT)) {
            throw new AccessDeniedException("Somente o perfil Suporte pode atribuir ROLE_SUPPORT.");
        }
    }

    private void assertCanManageTarget(Authentication authentication, User target) {
        if (ROLE_SUPPORT.equals(target.getRole()) && !hasRole(authentication, ROLE_SUPPORT)) {
            throw new AccessDeniedException(
                    "Somente o perfil Suporte pode alterar ou excluir usuários de suporte.");
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                    .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
