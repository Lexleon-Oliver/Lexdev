package net.ddns.lexdev.systempro_api.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer postgres =
        new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("systempro_test")
            .withUsername("test")
            .withPassword("test");
}
