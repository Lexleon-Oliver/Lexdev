package net.ddns.lexdev.systempro_api.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityNotFoundException;

@Service
public class FileSystemProductImageStorageService
        implements ProductImageStorageService {

    private final Path rootLocation;

    public FileSystemProductImageStorageService(
        @Value("${systempro.images.storage-location}")
        String storageLocation
    ) {

        this.rootLocation = Paths
            .get(storageLocation)
            .toAbsolutePath()
            .normalize();

        try {

            Files.createDirectories(
                this.rootLocation
            );

        } catch (IOException ex) {

            throw new IllegalStateException(
                "Não foi possível criar o diretório de imagens: "
                    + this.rootLocation,
                ex
            );
        }
    }

    @Override
    public String store(
        Long productId,
        MultipartFile file
    ) {

        String originalFileName =
            file.getOriginalFilename();

        String extension = "";

        if (originalFileName != null) {

            int index =
                originalFileName.lastIndexOf('.');

            if (index >= 0) {
                extension =
                    originalFileName.substring(index);
            }
        }

        String storedFileName =
            UUID.randomUUID() + extension;

        Path productDirectory =
            rootLocation.resolve(
                String.valueOf(productId)
            ).normalize();

        try {

            Files.createDirectories(
                productDirectory
            );

            Path target =
                productDirectory
                    .resolve(storedFileName)
                    .normalize();

            if (!target.startsWith(productDirectory)) {

                throw new IllegalStateException(
                    "Caminho de arquivo inválido."
                );
            }

            Files.copy(
                file.getInputStream(),
                target,
                StandardCopyOption.REPLACE_EXISTING
            );

            return rootLocation
                .relativize(target)
                .toString()
                .replace("\\", "/");

        } catch (IOException ex) {

            throw new IllegalStateException(
                "Não foi possível armazenar a imagem.",
                ex
            );
        }
    }

    @Override
    public Resource load(
        String storagePath
    ) {

        try {

            Path file =
                rootLocation
                    .resolve(storagePath)
                    .normalize();

            if (!file.startsWith(rootLocation)) {

                throw new IllegalStateException(
                    "Caminho de imagem inválido."
                );
            }

            Resource resource =
                new UrlResource(file.toUri());

            if (!resource.exists() ||
                !resource.isReadable()) {

                throw new EntityNotFoundException(
                    "Arquivo não encontrado: "
                        + storagePath
                );
            }

            return resource;

        } catch (MalformedURLException ex) {

            throw new IllegalStateException(
                "Não foi possível carregar a imagem.",
                ex
            );
        }
    }

    @Override
    public void delete(
        String storagePath
    ) {

        try {

            Path file =
                rootLocation
                    .resolve(storagePath)
                    .normalize();

            if (!file.startsWith(rootLocation)) {

                throw new IllegalStateException(
                    "Caminho de imagem inválido."
                );
            }

            Files.deleteIfExists(file);

        } catch (IOException ex) {

            throw new IllegalStateException(
                "Não foi possível excluir a imagem.",
                ex
            );
        }
    }
}