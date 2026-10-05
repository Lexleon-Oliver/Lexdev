package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query(value = """
        WITH expired_tokens AS (
            SELECT id
            FROM public.tb_refresh_tokens
            WHERE expiry_date <
                  CURRENT_TIMESTAMP
                  - CAST(:gracePeriodSeconds AS double precision)
                    * INTERVAL '1 second'
            ORDER BY expiry_date ASC, id ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        )
        DELETE FROM public.tb_refresh_tokens r
        USING expired_tokens e
        WHERE r.id = e.id
        """, nativeQuery = true)
    int deleteExpiredBatch(
            @Param("gracePeriodSeconds") long gracePeriodSeconds,
            @Param("batchSize") int batchSize
    );

    void deleteByUsername(String username);
}