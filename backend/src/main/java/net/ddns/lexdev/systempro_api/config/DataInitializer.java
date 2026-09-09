package net.ddns.lexdev.systempro_api.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.repository.UserRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User(
                "admin",
                "Administrator",
                "admin@lexdev.net.br",
                passwordEncoder.encode("admin123"),
                "ROLE_ADMIN"
                
            );
            userRepository.save(admin);
        }
    }
}
