package net.ddns.lexdev.systempro_api.service.backup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import net.ddns.lexdev.systempro_api.config.BackupProperties;
import net.ddns.lexdev.systempro_api.exception.BusinessException;

@Component
public class PostgreSqlDumpExecutor {

    private final BackupProperties properties;

    public PostgreSqlDumpExecutor(BackupProperties properties) {
        this.properties = properties;
    }

    public void dump(Path outputFile) {
        List<String> command = List.of(
            "pg_dump",
            "--format=custom",
            "--no-owner",
            "--no-privileges",
            "--host=" + properties.databaseHost(),
            "--port=" + properties.databasePort(),
            "--username=" + properties.databaseUsername(),
            "--file=" + outputFile.toAbsolutePath(),
            properties.databaseName()
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processBuilder.environment().put("PGPASSWORD", properties.databasePassword());

        Path errorFile = outputFile.resolveSibling("pg_dump.stderr.log");
        processBuilder.redirectError(errorFile.toFile());

        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(properties.pgDumpTimeoutSeconds(), TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("O backup do PostgreSQL excedeu o tempo limite de "
                    + Duration.ofSeconds(properties.pgDumpTimeoutSeconds()).toMinutes() + " minuto(s).");
            }

            if (process.exitValue() != 0) {
                String stderr = Files.exists(errorFile)
                    ? Files.readString(errorFile, StandardCharsets.UTF_8).trim()
                    : "";
                throw new BusinessException("Não foi possível gerar o backup do PostgreSQL."
                    + (stderr.isBlank() ? "" : " Detalhe: " + sanitize(stderr)));
            }

            if (!Files.isRegularFile(outputFile) || Files.size(outputFile) == 0) {
                throw new BusinessException("O pg_dump terminou sem produzir um arquivo de backup válido.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("A geração do backup do PostgreSQL foi interrompida.");
        } catch (IOException ex) {
            throw new BusinessException("Não foi possível executar o pg_dump: " + ex.getMessage());
        } finally {
            try {
                Files.deleteIfExists(errorFile);
            } catch (IOException ignored) {
                // Arquivo temporário de diagnóstico; a falha na limpeza não invalida o dump.
            }
        }
    }

    private String sanitize(String value) {
        String sanitized = value.replace(properties.databasePassword(), "***");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }
}