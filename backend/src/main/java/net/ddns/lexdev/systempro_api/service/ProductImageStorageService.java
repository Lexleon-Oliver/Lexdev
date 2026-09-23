package net.ddns.lexdev.systempro_api.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface ProductImageStorageService {

    String store(Long productId, MultipartFile file);

    Resource load(String storagePath);

    void delete(String storagePath);
}
