package net.ddns.lexdev.systempro_api.repository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import net.ddns.lexdev.systempro_api.domain.RefreshToken;

@Testcontainers
@DataJpaTest
class RefreshTokenRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RefreshTokenRepository repository;

    @Test
    void deveRemoverSomenteTokensExpiradosAlemDaMargem() {
        Instant now = Instant.now();

        RefreshToken expiredUnused = new RefreshToken(
                "jti-expired-unused",
                "user1",
                now.minusSeconds(7200)
        );

        RefreshToken expiredRevoked = new RefreshToken(
                "jti-expired-revoked",
                "user1",
                now.minusSeconds(7200)
        );
        expiredRevoked.setRevoked(true);

        RefreshToken insideGracePeriod = new RefreshToken(
                "jti-inside-grace",
                "user1",
                now.minusSeconds(1800)
        );

        RefreshToken valid = new RefreshToken(
                "jti-valid",
                "user1",
                now.plusSeconds(3600)
        );

        repository.save(expiredUnused);
        repository.save(expiredRevoked);
        repository.save(insideGracePeriod);
        repository.save(valid);

        repository.flush();

        int deleted = repository.deleteExpiredBatch(
                3600,
                1000
        );

        assertThat(deleted).isEqualTo(2);

        assertThat(repository.findByJtiForUpdate("jti-expired-unused"))
                .isEmpty();

        assertThat(repository.findByJtiForUpdate("jti-expired-revoked"))
                .isEmpty();

        assertThat(repository.findByJtiForUpdate("jti-inside-grace"))
                .isPresent();

        assertThat(repository.findByJtiForUpdate("jti-valid"))
                .isPresent();
    }
}
