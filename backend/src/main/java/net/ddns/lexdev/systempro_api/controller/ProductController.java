package net.ddns.lexdev.systempro_api.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import net.ddns.lexdev.systempro_api.dto.ProductRequestDto;
import net.ddns.lexdev.systempro_api.dto.ProductResponseDto;
import net.ddns.lexdev.systempro_api.service.ProductImageService;
import net.ddns.lexdev.systempro_api.service.ProductService;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final ProductImageService productImageService;

    public ProductController(ProductService productService, ProductImageService productImageService) {
        this.productService = productService;
        this.productImageService = productImageService;
    }

    // ============================================================
    // CREATE
    // ============================================================

    @PostMapping
    public ResponseEntity<ProductResponseDto> create(
        @Valid @RequestBody ProductRequestDto dto
    ) {

        ProductResponseDto response =
            productService.create(dto);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }

    // ============================================================
    // UPDATE
    // ============================================================

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDto> update(
        @PathVariable Long id,
        @Valid @RequestBody ProductRequestDto dto
    ) {

        ProductResponseDto response =
            productService.update(id, dto);

        return ResponseEntity.ok(response);
    }

    // ============================================================
    // FIND BY ID
    // ============================================================

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDto> findById(
        @PathVariable Long id
    ) {

        return ResponseEntity.ok(
            productService.findById(id)
        );
    }

    // ============================================================
    // LIST
    // ============================================================

    @GetMapping
    public ResponseEntity<Page<ProductResponseDto>> findAll(
        @PageableDefault(size = 20, sort = "name")
        Pageable pageable
    ) {

        return ResponseEntity.ok(
            productService.findAll(pageable)
        );
    }

    // ============================================================
    // SEARCH
    // ============================================================

    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponseDto>> search(
        @RequestParam String q,
        @PageableDefault(size = 20, sort = "name")
        Pageable pageable
    ) {

        return ResponseEntity.ok(
            productService.search(q, pageable)
        );
    }

    // ============================================================
    // SOFT DELETE
    // ============================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable Long id
    ) {

        productService.delete(id);

        return ResponseEntity.noContent().build();
    }

   
}
