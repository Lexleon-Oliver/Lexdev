package net.ddns.lexdev.systempro_api.controller;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import net.ddns.lexdev.systempro_api.dto.ProductImageResponseDto;
import net.ddns.lexdev.systempro_api.service.ProductImageService;

@RestController
@RequestMapping("/products/{productId}/images")
public class ProductImageController {

    private final ProductImageService productImageService;

    public ProductImageController(
        ProductImageService productImageService
    ) {
        this.productImageService = productImageService;
    }

    // ============================================================
    // LIST IMAGES
    // ============================================================

    @GetMapping
    public ResponseEntity<List<ProductImageResponseDto>> findAll(
        @PathVariable Long productId
    ) {

        return ResponseEntity.ok(
            productImageService.findAll(productId)
        );
    }

    // ============================================================
    // UPLOAD
    // ============================================================

    @PostMapping(
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ProductImageResponseDto> upload(
        @PathVariable Long productId,
        @RequestParam("file") MultipartFile file
    ) {

        ProductImageResponseDto response =
            productImageService.upload(
                productId,
                file
            );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }

    // ============================================================
    // IMAGE CONTENT
    // ============================================================

    @GetMapping("/{imageId}/content")
    public ResponseEntity<Resource> getContent(
        @PathVariable Long productId,
        @PathVariable Long imageId
    ) {

        return productImageService.getContent(
            productId,
            imageId
        );
    }

    // ============================================================
    // SET MAIN IMAGE
    // ============================================================

    @PostMapping("/{imageId}/main")
    public ResponseEntity<ProductImageResponseDto> setMainImage(
        @PathVariable Long productId,
        @PathVariable Long imageId
    ) {

        return ResponseEntity.ok(
            productImageService.setMainImage(
                productId,
                imageId
            )
        );
    }

    // ============================================================
    // DELETE
    // ============================================================

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(
        @PathVariable Long productId,
        @PathVariable Long imageId
    ) {

        productImageService.delete(
            productId,
            imageId
        );

        return ResponseEntity.noContent().build();
    }
}
