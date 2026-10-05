package net.ddns.lexdev.systempro_api.storage;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class FileValidationService {

    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS = Map.of(
        "application/pdf", Set.of("pdf"),
        "image/jpeg", Set.of("jpg", "jpeg"),
        "image/png", Set.of("png")
    );

    private final Tika tika = new Tika();

    public ValidatedFile validate(MultipartFile multipartFile, long maxFileSize) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("O arquivo é obrigatório.");
        }

        if (maxFileSize <= 0) {
            throw new IllegalStateException(
                "O limite máximo de arquivo deve ser maior que zero."
            );
        }

        if (multipartFile.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                "O arquivo excede o tamanho máximo permitido."
            );
        }

        String originalFileName = sanitizeFileName(
            multipartFile.getOriginalFilename()
        );
        String extension = extensionOf(originalFileName);
        Path tempFile = null;

        try {
            tempFile = Files.createTempFile("systempro-upload-", ".tmp");
            multipartFile.transferTo(tempFile);

            long actualFileSize = Files.size(tempFile);
            if (actualFileSize <= 0 || actualFileSize > maxFileSize) {
                throw new IllegalArgumentException(
                    "O arquivo excede o tamanho máximo permitido."
                );
            }

            String detectedContentType = tika.detect(tempFile);
            Set<String> allowedExtensions =
                ALLOWED_EXTENSIONS.get(detectedContentType);

            if (allowedExtensions == null
                || !allowedExtensions.contains(extension)) {
                throw new IllegalArgumentException(
                    "Tipo de arquivo não permitido."
                );
            }

            return new ValidatedFile(
                tempFile,
                originalFileName,
                detectedContentType,
                actualFileSize
            );
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new IllegalStateException(
                "Não foi possível preparar o arquivo para validação.", e
            );
        } catch (RuntimeException e) {
            deleteQuietly(tempFile);
            throw e;
        }
    }

    private String sanitizeFileName(String original) {
        String value = StringUtils.hasText(original) ? original : "arquivo";
        value = value.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1);
        value = value.replaceAll("[\\p{Cntrl}]", "_").trim();
        if (value.isBlank()) {
            value = "arquivo";
        }
        return value.length() <= 255
            ? value
            : value.substring(0, 255);
    }

    public String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    public record ValidatedFile(
        Path path,
        String originalFileName,
        String contentType,
        long fileSize
    ) {}
}
