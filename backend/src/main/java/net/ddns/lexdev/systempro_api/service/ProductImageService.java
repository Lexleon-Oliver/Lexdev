package net.ddns.lexdev.systempro_api.service;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.domain.Product;
import net.ddns.lexdev.systempro_api.domain.ProductImage;
import net.ddns.lexdev.systempro_api.dto.ProductImageResponseDto;
import net.ddns.lexdev.systempro_api.repository.ProductImageRepository;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;


@Service
public class ProductImageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        MediaType.IMAGE_JPEG_VALUE,
        MediaType.IMAGE_PNG_VALUE,
        "image/webp"
    );

    private static final long DEFAULT_MAX_FILE_SIZE =
        5 * 1024 * 1024L;

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductImageStorageService storageService;
    private final long maxFileSize;

    public ProductImageService(
        ProductRepository productRepository,
        ProductImageRepository productImageRepository,
        ProductImageStorageService storageService,
        @Value("${systempro.images.max-file-size-bytes:5242880}")
        long maxFileSize
    ) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.storageService = storageService;

        this.maxFileSize = maxFileSize > 0
            ? maxFileSize
            : DEFAULT_MAX_FILE_SIZE;
    }

    // ============================================================
    // LIST
    // ============================================================

    @Transactional(readOnly = true)
    public List<ProductImageResponseDto> findAll(
        Long productId
    ) {

        findProduct(productId);

        return productImageRepository
            .findByProductIdOrderBySortOrderAsc(productId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    // ============================================================
    // UPLOAD
    // ============================================================

    @Transactional
    public ProductImageResponseDto upload(
        Long productId,
        MultipartFile file
    ) {

        Product product = findProduct(productId);

        validateFile(file);

        boolean hasMainImage =
            productImageRepository
                .existsByProductIdAndMainImageTrue(productId);

        Integer sortOrder =
            productImageRepository
                .findMaxSortOrder(productId)
                .orElse(-1) + 1;

        String storagePath = null;

        try {

            storagePath = storageService.store(
                productId,
                file
            );

            ProductImage image = new ProductImage();

            image.setProduct(product);

            image.setFileName(
                resolveOriginalFileName(file)
            );

            image.setStoragePath(storagePath);

            image.setContentType(
                file.getContentType()
            );

            image.setFileSize(
                file.getSize()
            );

            image.setMainImage(!hasMainImage);

            image.setSortOrder(sortOrder);

            ProductImage saved =
                productImageRepository.save(image);

            return toResponse(saved);

        } catch (RuntimeException ex) {

            if (storagePath != null) {
                try {
                    storageService.delete(storagePath);
                } catch (RuntimeException ignored) {
                    // Preserva a exceção original.
                }
            }

            throw ex;
        }
    }

    // ============================================================
    // CONTENT
    // ============================================================

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> getContent(
        Long productId,
        Long imageId
    ) {

        ProductImage image =
            findImage(productId, imageId);

        Resource resource =
            storageService.load(
                image.getStoragePath()
            );

        MediaType mediaType =
            resolveMediaType(
                image.getContentType()
            );

        return ResponseEntity
            .ok()
            .contentType(mediaType)
            .body(resource);
    }

    // ============================================================
    // SET MAIN IMAGE
    // ============================================================

    @Transactional
    public ProductImageResponseDto setMainImage(
        Long productId,
        Long imageId
    ) {

        ProductImage selected =
            findImage(productId, imageId);

        List<ProductImage> images =
            productImageRepository
                .findByProductIdOrderBySortOrderAsc(productId);

        for (ProductImage image : images) {
            image.setMainImage(
                image.getId().equals(selected.getId())
            );
        }

        return toResponse(selected);
    }

    // ============================================================
    // DELETE
    // ============================================================

    @Transactional
    public void delete(
        Long productId,
        Long imageId
    ) {

        ProductImage image =
            findImage(productId, imageId);

        boolean deletingMain =
            image.isMainImage();

        String storagePath =
            image.getStoragePath();

        ProductImage nextMain = null;

        if (deletingMain) {

            List<ProductImage> images =
                productImageRepository
                    .findByProductIdOrderBySortOrderAsc(productId);

            nextMain = images
                .stream()
                .filter(other ->
                    !other.getId().equals(imageId)
                )
                .findFirst()
                .orElse(null);
        }

        if (nextMain != null) {
            nextMain.setMainImage(true);
        }

        productImageRepository.delete(image);
        productImageRepository.flush();

        storageService.delete(storagePath);
    }

    // ============================================================
    // FIND PRODUCT
    // ============================================================

    private Product findProduct(Long productId) {

        return productRepository
            .findById(productId)
            .orElseThrow(() ->
                new EntityNotFoundException(
                    "Produto não encontrado: " + productId
                )
            );
    }

    // ============================================================
    // FIND IMAGE
    // ============================================================

    private ProductImage findImage(
        Long productId,
        Long imageId
    ) {

        return productImageRepository
            .findByIdAndProductId(
                imageId,
                productId
            )
            .orElseThrow(() ->
                new EntityNotFoundException(
                    "Imagem não encontrada: " + imageId
                )
            );
    }

    // ============================================================
    // VALIDATION
    // ============================================================

    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                "Arquivo de imagem não informado."
            );
        }

        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                "A imagem excede o tamanho máximo permitido de "
                    + (maxFileSize / 1024 / 1024)
                    + " MB."
            );
        }

        String contentType =
            file.getContentType();

        if (contentType == null ||
            !ALLOWED_CONTENT_TYPES.contains(contentType)) {

            throw new IllegalArgumentException(
                "Formato de imagem não suportado. "
                    + "Utilize JPEG, PNG ou WebP."
            );
        }
    }

    // ============================================================
    // FILE NAME
    // ============================================================

    private String resolveOriginalFileName(
        MultipartFile file
    ) {

        String fileName =
            file.getOriginalFilename();

        if (fileName == null ||
            fileName.isBlank()) {

            return "imagem";
        }

        return fileName;
    }

    // ============================================================
    // MEDIA TYPE
    // ============================================================

    private MediaType resolveMediaType(
        String contentType
    ) {

        if (contentType == null ||
            contentType.isBlank()) {

            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {

            return MediaType.parseMediaType(
                contentType
            );

        } catch (IllegalArgumentException ex) {

            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    // ============================================================
    // RESPONSE
    // ============================================================

    private ProductImageResponseDto toResponse(
        ProductImage image
    ) {

        Long productId =
            image.getProduct().getId();

        String url =
            "/api/products/"
                + productId
                + "/images/"
                + image.getId()
                + "/content";

        return new ProductImageResponseDto(
            image.getId(),
            image.getFileName(),
            image.getContentType(),
            image.getFileSize(),
            image.isMainImage(),
            image.getSortOrder(),
            url
        );
    }
}