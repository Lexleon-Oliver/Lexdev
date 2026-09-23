package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.ddns.lexdev.systempro_api.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByCode(String code);

    boolean existsByGtin(String gtin);

    Optional<Product> findByGtin(String gtin);

    @Query("""
        SELECT p
        FROM Product p
        WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(p.code) LIKE LOWER(CONCAT('%', :search, '%'))
        """)
    Page<Product> search(
        @Param("search") String search,
        Pageable pageable
    );

    @Query("""
        SELECT p
        FROM Product p
        """)
    Page<Product> findAllProducts(Pageable pageable);

    @Query("""
        SELECT DISTINCT p
        FROM Product p
        LEFT JOIN FETCH p.suppliers ps
        LEFT JOIN FETCH ps.supplier
        WHERE p.id = :id
        """)
    Optional<Product> findByIdWithSuppliers(
        @Param("id") Long id
    );
}