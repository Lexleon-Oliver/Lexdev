package net.ddns.lexdev.systempro_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.ddns.lexdev.systempro_api.domain.ProductImage;

public interface ProductImageRepository
        extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderBySortOrderAsc(
        Long productId
    );

    boolean existsByProductIdAndMainImageTrue(
        Long productId
    );

    Optional<ProductImage> findByIdAndProductId(
        Long imageId,
        Long productId
    );

    @Query("""
        SELECT MAX(i.sortOrder)
        FROM ProductImage i
        WHERE i.product.id = :productId
        """)
    Optional<Integer> findMaxSortOrder(
        @Param("productId") Long productId
    );
}