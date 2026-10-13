package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import net.ddns.lexdev.systempro_api.domain.User;
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

        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(java.util.Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(1L, new UserUpdateDto("Maria Silva", "nova@example.com", "ROLE_ADMIN"));

        assertThat(user.isActive()).isTrue();
        assertThat(user.getName()).isEqualTo("Maria Silva");
        assertThat(user.getEmail()).isEqualTo("nova@example.com");
        assertThat(user.getRole()).isEqualTo("ROLE_ADMIN");
    }
}