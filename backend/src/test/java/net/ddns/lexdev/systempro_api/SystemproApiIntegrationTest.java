package net.ddns.lexdev.systempro_api;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import net.ddns.lexdev.systempro_api.integration.IntegrationTestBase;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class SystemproApiIntegrationTest extends IntegrationTestBase {

      @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deveCarregarContextoComPostgresqlReal() {
        Integer resultado = jdbcTemplate.queryForObject(
            "SELECT 1",
            Integer.class
        );

        assertThat(resultado).isEqualTo(1);
    }

    @Test
    void deveEstarConectadoAoBancoDeTestes() {
        String databaseName = jdbcTemplate.queryForObject(
            "SELECT current_database()",
            String.class
        );

        assertThat(databaseName).isEqualTo("systempro_test");
    }
}