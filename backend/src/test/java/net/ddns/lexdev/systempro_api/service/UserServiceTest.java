package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.dto.UserCreateDto;
import net.ddns.lexdev.systempro_api.dto.UserUpdateDto;
import net.ddns.lexdev.systempro_api.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldNormalizeSearchAndDelegatePaginationToRepository() {
        UserService service = new UserService(userRepository, passwordEncoder);
        PageRequest pageable = PageRequest.of(2, 10);
        User user = new User("maria", "Maria Silva", "maria@example.com", "hash", "ROLE_USER");

        when(userRepository.search(eq("maria"), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(user), pageable, 21));

        var result = service.findAll(pageable, "  MARIA  ");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(21);
        verify(userRepository).search("maria", pageable);
    }

    @Test
    void updateMustNotDeactivateUser() {
        UserService service = new UserService(userRepository, passwordEncoder);
        User user = new User("maria", "Maria", "maria@example.com", "hash", "ROLE_USER");
        user.setActive(true);

        when(userRepository.findByIdAndActiveTrue(1L))
            .thenReturn(java.util.Optional.of(user));
        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.update(
            1L,
            new UserUpdateDto("Maria Silva", "nova@example.com", "ROLE_ADMIN"),
            authentication("admin", "ROLE_ADMIN")
        );

        assertThat(user.isActive()).isTrue();
        assertThat(user.getName()).isEqualTo("Maria Silva");
        assertThat(user.getEmail()).isEqualTo("nova@example.com");
        assertThat(user.getRole()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void adminMustNotCreateSupportUser() {
        UserService service = new UserService(userRepository, passwordEncoder);
        UserCreateDto dto = supportCreateDto();

        assertThatThrownBy(() ->
            service.register(dto, authentication("admin", "ROLE_ADMIN"))
        ).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void supportMayCreateSupportUser() {
        UserService service = new UserService(userRepository, passwordEncoder);
        UserCreateDto dto = supportCreateDto();

        when(userRepository.existsByUsername("partner")).thenReturn(false);
        when(userRepository.existsByEmail("partner@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Senha123@")).thenReturn("hash");
        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.register(dto, authentication("@support", "ROLE_SUPPORT"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User saved = userCaptor.getValue();
        assertThat(saved.getUsername()).isEqualTo("partner");
        assertThat(saved.getRole()).isEqualTo("ROLE_SUPPORT");
        assertThat(saved.getPassword()).isEqualTo("hash");
    }

    @Test
    void adminMustNotPromoteUserToSupport() {
        UserService service = new UserService(userRepository, passwordEncoder);
        User user = new User("maria", "Maria", "maria@example.com", "hash", "ROLE_USER");

        when(userRepository.findByIdAndActiveTrue(1L))
            .thenReturn(java.util.Optional.of(user));

        assertThatThrownBy(() ->
            service.update(
                1L,
                new UserUpdateDto("Maria", "maria@example.com", "ROLE_SUPPORT"),
                authentication("admin", "ROLE_ADMIN")
            )
        ).isInstanceOf(AccessDeniedException.class);

        assertThat(user.getRole()).isEqualTo("ROLE_USER");
    }

    @Test
    void adminMustNotEditExistingSupportUser() {
        UserService service = new UserService(userRepository, passwordEncoder);
        User support = new User("@support", "Suporte", "support@example.com", "hash", "ROLE_SUPPORT");

        when(userRepository.findByIdAndActiveTrue(1L))
            .thenReturn(java.util.Optional.of(support));

        assertThatThrownBy(() ->
            service.update(
                1L,
                new UserUpdateDto("Novo nome", "support@example.com", "ROLE_ADMIN"),
                authentication("admin", "ROLE_ADMIN")
            )
        ).isInstanceOf(AccessDeniedException.class);

        assertThat(support.getName()).isEqualTo("Suporte");
        assertThat(support.getRole()).isEqualTo("ROLE_SUPPORT");
    }

    @Test
    void adminMustNotDeleteExistingSupportUser() {
        UserService service = new UserService(userRepository, passwordEncoder);
        User support = new User("@support", "Suporte", "support@example.com", "hash", "ROLE_SUPPORT");
        support.setActive(true);

        when(userRepository.findByIdAndActiveTrue(1L))
            .thenReturn(java.util.Optional.of(support));

        assertThatThrownBy(() ->
            service.delete(1L, authentication("admin", "ROLE_ADMIN"))
        ).isInstanceOf(AccessDeniedException.class);

        assertThat(support.isActive()).isTrue();
    }

    @Test
    void supportMayEditExistingSupportUser() {
        UserService service = new UserService(userRepository, passwordEncoder);
        User support = new User("@support", "Suporte", "support@example.com", "hash", "ROLE_SUPPORT");
        support.setActive(true);

        when(userRepository.findByIdAndActiveTrue(1L))
            .thenReturn(java.util.Optional.of(support));
        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.update(
            1L,
            new UserUpdateDto("Suporte Lexdev", "support@example.com", "ROLE_SUPPORT"),
            authentication("@support", "ROLE_SUPPORT")
        );

        assertThat(support.getName()).isEqualTo("Suporte Lexdev");
        assertThat(support.getRole()).isEqualTo("ROLE_SUPPORT");
    }

    private static UserCreateDto supportCreateDto() {
        return new UserCreateDto(
            "partner",
            "Parceiro",
            "Parceiro Comercial",
            "partner@example.com",
            "Senha123@",
            "ROLE_SUPPORT"
        );
    }

    private static Authentication authentication(String username, String role) {
        return new UsernamePasswordAuthenticationToken(
            username,
            "n/a",
            List.of(new SimpleGrantedAuthority(role))
        );
    }
}
