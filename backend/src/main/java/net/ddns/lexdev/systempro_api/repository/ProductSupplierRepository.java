package net.ddns.lexdev.systempro_api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import net.ddns.lexdev.systempro_api.domain.ProductSupplier;

public interface ProductSupplierRepository
        extends JpaRepository<ProductSupplier, Long> {

    List<ProductSupplier> findByProductId(Long productId);

    boolean existsByProductIdAndSupplierId(
        Long productId,
        Long supplierId
    );
}
