package net.ddns.lexdev.systempro_api.service.backup;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import net.ddns.lexdev.systempro_api.config.BackupProperties;
import net.ddns.lexdev.systempro_api.dto.backup.BackupManifest;
import net.ddns.lexdev.systempro_api.dto.backup.BackupResponseDto;
import net.ddns.lexdev.systempro_api.dto.backup.BackupTrigger;
import net.ddns.lexdev.systempro_api.exception.BusinessException;

@Service
public class BackupService {

    private static final DateTimeFormatter FILE_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final BackupProperties properties;
    private final PostgreSqlDumpExecutor dumpExecutor;
    private final BackupPackageService packageService;
    private final BackupPackageVerifier packageVerifier;
    private final Clock clock;
    private final ReentrantLock generationLock = new ReentrantLock();

    @Autowired
    public BackupService(
        BackupProperties properties,
        PostgreSqlDumpExecutor dumpExecutor,
        BackupPackageService packageService,
        BackupPackageVerifier packageVerifier
    ) {
        this(properties, dumpExecutor, packageService, packageVerifier, Clock.systemUTC());
    }

    BackupService(
        BackupProperties properties,
        PostgreSqlDumpExecutor dumpExecutor,
        BackupPackageService packageService,
        BackupPackageVerifier packageVerifier,
        Clock clock
    ) {
        this.properties = properties;
        this.dumpExecutor = dumpExecutor;
        this.packageService = packageService;
        this.packageVerifier = packageVerifier;
        this.clock = clock;
    }

    public BackupResponseDto generate() {
        return generate(BackupTrigger.MANUAL, true).orElseThrow();
    }

    public Optional<BackupResponseDto> generateScheduled() {
        return generate(BackupTrigger.SCHEDULED, false);
    }

    private Optional<BackupResponseDto> generate(BackupTrigger trigger, boolean failWhenBusy) {
        validateConfiguration();
        if (!generationLock.tryLock()) {
            if (failWhenBusy) {
                throw new BusinessException("Já existe um backup em geração. Aguarde a conclusão antes de iniciar outro.");
            }
            return Optional.empty();
        }

        Path workspace = null;
        try {
            Path backupDirectory = backupDirectory();
            Files.createDirectories(backupDirectory);
            workspace = Files.createTempDirectory(backupDirectory, ".work-");

            Instant startedAt = clock.instant();
            Path dump = workspace.resolve("systempro.dump");
            dumpExecutor.dump(dump);

            Instant databaseDumpCompletedAt = clock.instant();
            BackupManifest manifest = new BackupManifest(
                1,
                startedAt,
                databaseDumpCompletedAt,
                "PostgreSQL",
                properties.databaseName(),
                properties.storageProvider(),
                List.of("database", "product-images", "supplier-documents"),
                trigger
            );

            String fileName = "systempro-backup-" + FILE_TIMESTAMP.format(startedAt) + ".spbackup";
            Path temporaryPackage = workspace.resolve(fileName + ".part");
            packageService.createPackage(
                temporaryPackage,
                dump,
                Path.of(properties.productImagesLocation()),
                Path.of(properties.documentsLocation()),
                manifest
            );
            packageVerifier.verify(temporaryPackage);

            Path finalPackage = backupDirectory.resolve(fileName);
            moveCompletedPackage(temporaryPackage, finalPackage);
            return Optional.of(toResponse(finalPackage));
        } catch (IOException ex) {
            throw new BusinessException("Não foi possível gerar o pacote de backup: " + ex.getMessage());
        } finally {
            deleteRecursively(workspace);
            generationLock.unlock();
        }
    }

    public List<BackupResponseDto> list() {
        Path directory = backupDirectory();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var stream = Files.list(directory)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".spbackup"))
                .map(this::toResponse)
                .sorted(Comparator.comparing(BackupResponseDto::createdAt).reversed())
                .toList();
        } catch (IOException ex) {
            throw new BusinessException("Não foi possível listar os backups locais: " + ex.getMessage());
        }
    }

    public Resource load(String fileName) {
        if (fileName == null || !fileName.matches("systempro-backup-[0-9]{8}T[0-9]{6}Z\\.spbackup")) {
            throw new BusinessException("Nome de arquivo de backup inválido.");
        }
        Path file = backupDirectory().resolve(fileName).normalize();
        if (!file.getParent().equals(backupDirectory().normalize()) || !Files.isRegularFile(file)) {
            throw new BusinessException("Backup não encontrado.");
        }
        return new FileSystemResource(file);
    }

    private void validateConfiguration() {
        if (!"local".equalsIgnoreCase(properties.storageProvider())) {
            throw new BusinessException(
                "A Fase 1 do backup completo suporta apenas systempro.storage.provider=local."
            );
        }
        requireText(properties.databaseHost(), "host do PostgreSQL");
        requireText(properties.databaseName(), "nome do banco PostgreSQL");
        requireText(properties.databaseUsername(), "usuário do PostgreSQL");
        requireText(properties.databasePassword(), "senha do PostgreSQL");
        requireText(properties.storageLocation(), "diretório de backups");
        requireText(properties.productImagesLocation(), "diretório de imagens de produtos");
        requireText(properties.documentsLocation(), "diretório de documentos");
        if (properties.databasePort() <= 0 || properties.databasePort() > 65535) {
            throw new BusinessException("Porta do PostgreSQL inválida para backup.");
        }
        if (properties.pgDumpTimeoutSeconds() <= 0) {
            throw new BusinessException("Timeout do pg_dump deve ser maior que zero.");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Configuração obrigatória ausente: " + field + ".");
        }
    }

    private Path backupDirectory() {
        return Path.of(properties.storageLocation()).toAbsolutePath().normalize();
    }

    private BackupResponseDto toResponse(Path file) {
        try {
            return new BackupResponseDto(
                file.getFileName().toString(),
                Files.size(file),
                Files.getLastModifiedTime(file).toInstant()
            );
        } catch (IOException ex) {
            throw new BusinessException("Não foi possível ler os metadados do backup "
                + file.getFileName() + ".");
        }
    }

    private void moveCompletedPackage(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }

    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Melhor esforço de limpeza do workspace temporário.
                }
            });
        } catch (IOException ignored) {
            // Melhor esforço de limpeza do workspace temporário.
        }
    }
}