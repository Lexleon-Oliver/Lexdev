package net.ddns.lexdev.systempro_api.storage;

import org.springframework.core.io.Resource;

import java.nio.file.Path;

public interface FileStorageService {
    void upload(String storageKey, Path file, String contentType);
    Resource download(String storageKey);
    void delete(String storageKey);
}
