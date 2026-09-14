package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import net.ddns.lexdev.systempro_api.domain.RefreshToken;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r
        from RefreshToken r
        where r.jti = :jti
    """)
    Optional<RefreshToken> findByJtiForUpdate(
            @Param("jti") String jti
    );

    void deleteByUsername(String username);
}