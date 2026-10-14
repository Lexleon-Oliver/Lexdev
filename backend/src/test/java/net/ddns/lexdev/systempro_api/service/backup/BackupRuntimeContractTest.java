package net.ddns.lexdev.systempro_api.service.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BackupRuntimeContractTest {

    @Test
    void dockerfileDeveUsarClientePostgresqlDaMesmaMajorDoServidor() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertThat(dockerfile).contains("apk add --no-cache postgresql16-client");
        assertThat(dockerfile).doesNotContain("apk add --no-cache postgresql-client");
    }
}