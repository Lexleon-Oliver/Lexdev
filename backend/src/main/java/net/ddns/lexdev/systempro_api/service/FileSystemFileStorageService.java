package net.ddns.lexdev.systempro_api.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import net.ddns.lexdev.systempro_api.config.StorageProperties;
import net.ddns.lexdev.systempro_api.storage.FileStorageService;

@Service
@ConditionalOnProperty(
    name = "systempro.storage.provider",
    havingValue = "local",
    matchIfMissing = true
)
public class FileSystemFileStorageService implements FileStorageService {

    private final Path rootLocation;

    public FileSystemFileStorageService(StorageProperties properties) {

        String storageLocation = properties.localStorageLocation();

        if (storageLocation == null || storageLocation.isBlank()) {
            throw new IllegalStateException(
                "systempro.storage.local-storage-location deve ser informado."
            );
        }

        this.rootLocation = Path
            .of(storageLocation)
            .toAbsolutePath()
            .normalize();

        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException ex) {
            throw new IllegalStateException(
                "Não foi possível criar o diretório de documentos: "
                    + this.rootLocation,
                ex
            );
        }
    }

    @Override
    public void upload(
        String storageKey,
        Path file,
        String contentType
    ) {
        Path target = resolveSafe(storageKey);

        try {
            Files.createDirectories(target.getParent());

            Files.copy(
                file,
                target,
                StandardCopyOption.REPLACE_EXISTING
            );

        } catch (IOException ex) {
            throw new IllegalStateException(
                "Não foi possível armazenar o documento.",
                ex
            );
        }
    }

    @Override
    public Resource download(String storageKey) {
        Path file = resolveSafe(storageKey);

        try {
            Resource resource = new UrlResource(file.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalStateException(
                    "Arquivo não encontrado: " + storageKey
                );
            }

            return resource;

        } catch (MalformedURLException ex) {
            throw new IllegalStateException(
                "Não foi possível carregar o documento.",
                ex
            );
        }
    }

    @Override
    public void delete(String storageKey) {
        Path file = resolveSafe(storageKey);

        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new IllegalStateException(
                "Não foi possível excluir o documento.",
                ex
            );
        }
    }

    private Path resolveSafe(String storageKey) {

        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException(
                "A chave de armazenamento é obrigatória."
            );
        }

        Path resolved = rootLocation
            .resolve(storageKey)
            .normalize()
            .toAbsolutePath();

        if (!resolved.startsWith(rootLocation)) {
            throw new IllegalStateException(
                "Caminho de armazenamento inválido."
            );
        }

        return resolved;
    }
}