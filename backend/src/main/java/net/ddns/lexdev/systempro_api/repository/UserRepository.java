package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import net.ddns.lexdev.systempro_api.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Page<User> findByActiveTrue(Pageable pageable);
    
    Optional<User> findByIdAndActiveTrue(Long id);
    
    Optional<User> findByUsernameAndActiveTrue(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
