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
        SELECT p FROM Product p
        WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(p.code) LIKE LOWER(CONCAT('%', :search, '%'))
           OR p.gtin = :search
        """)
    Page<Product> searchForSale(@Param("search") String search, Pageable pageable);

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

    @Query("""
        SELECT p.id, p.code, p.name, p.unitOfMeasure, p.controlsStock,
               p.minimumStock, p.maximumStock, p.reorderPoint, p.salePrice,
               COALESCE(SUM(CASE WHEN s.id IS NOT NULL AND s.status <> net.ddns.lexdev.systempro_api.enums.SaleStatus.CANCELADA THEN i.quantity ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN s.id IS NOT NULL AND s.status <> net.ddns.lexdev.systempro_api.enums.SaleStatus.CANCELADA THEN i.total ELSE 0 END), 0)
        FROM Product p
        LEFT JOIN SaleItem i ON i.product = p
        LEFT JOIN i.sale s ON s.saleAt >= :start AND s.saleAt < :endExclusive
        GROUP BY p.id, p.code, p.name, p.unitOfMeasure, p.controlsStock,
                 p.minimumStock, p.maximumStock, p.reorderPoint, p.salePrice
        ORDER BY p.name ASC, p.code ASC
        """)
    java.util.List<Object[]> stockReport(
        @Param("start") java.time.Instant start,
        @Param("endExclusive") java.time.Instant endExclusive
    );

}