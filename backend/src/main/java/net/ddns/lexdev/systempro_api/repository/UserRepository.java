package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.ddns.lexdev.systempro_api.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("""
        SELECT u
        FROM User u
        WHERE :term = ''
           OR LOWER(u.name) LIKE CONCAT('%', :term, '%')
           OR LOWER(u.username) LIKE CONCAT('%', :term, '%')
           OR LOWER(u.email) LIKE CONCAT('%', :term, '%')
        """)
    Page<User> search(@Param("term") String term, Pageable pageable);

    Optional<User> findByIdAndActiveTrue(Long id);
    Optional<User> findByUsernameAndActiveTrue(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}