package net.ddns.lexdev.systempro_api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import net.ddns.lexdev.systempro_api.domain.ProductImage;

public interface ProductImageRepository
        extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);
}
