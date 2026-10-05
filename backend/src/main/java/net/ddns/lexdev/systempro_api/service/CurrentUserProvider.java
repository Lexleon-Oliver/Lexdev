package net.ddns.lexdev.systempro_api.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.repository.UserRepository;

@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireUser() {
        Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {

            throw new AccessDeniedException(
                "Usuário autenticado não encontrado."
            );
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof User user) {
            if (!user.isActive()) {
                throw new AccessDeniedException(
                    "Usuário autenticado está inativo."
                );
            }

            return user;
        }

        String username = null;

        if (principal instanceof UserDetails userDetails) {
            username = userDetails.getUsername();
        } else if (principal instanceof String principalString
                   && !principalString.isBlank()
                   && !"anonymousUser".equals(principalString)) {
            username = principalString;
        }

        if (username == null || username.isBlank()) {
            throw new AccessDeniedException(
                "Principal autenticado inválido."
            );
        }

        return userRepository.findByUsernameAndActiveTrue(username)
            .orElseThrow(() ->
                new AccessDeniedException(
                    "Usuário autenticado não encontrado."
                )
            );
    }
}