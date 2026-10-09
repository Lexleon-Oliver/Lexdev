package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import net.ddns.lexdev.systempro_api.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForStockUpdate(@Param("id") Long id);

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

    @Query(value = """
        WITH sales AS (
            SELECT i.product_id,
                   COALESCE(SUM(i.quantity), 0) AS quantity_sold,
                   COALESCE(SUM(i.total), 0) AS sales_value
              FROM tb_sale_item i
              JOIN tb_sale s ON s.id = i.sale_id
             WHERE s.sale_at >= :start
               AND s.sale_at < :endExclusive
               AND s.status <> 'CANCELADA'
             GROUP BY i.product_id
        ), movements AS (
            SELECT m.product_id,
                   COALESCE(SUM(CASE
                       WHEN m.movement_type IN ('INITIAL_BALANCE', 'PURCHASE_ENTRY', 'SALE_CANCELLATION_RETURN', 'POSITIVE_ADJUSTMENT')
                       THEN m.quantity ELSE 0 END), 0) AS stock_entries,
                   COALESCE(SUM(CASE
                       WHEN m.movement_type IN ('SALE_OUT', 'NEGATIVE_ADJUSTMENT')
                       THEN m.quantity ELSE 0 END), 0) AS stock_outputs
              FROM tb_stock_movement m
             WHERE m.created_at >= :start
               AND m.created_at < :endExclusive
             GROUP BY m.product_id
        )
        SELECT p.id AS productId,
               p.code AS code,
               p.name AS name,
               p.unit_of_measure AS unitOfMeasure,
               p.controls_stock AS controlsStock,
               p.minimum_stock AS minimumStock,
               p.maximum_stock AS maximumStock,
               p.reorder_point AS reorderPoint,
               p.sale_price AS salePrice,
               b.quantity AS currentBalance,
               COALESCE(s.quantity_sold, 0) AS quantitySold,
               COALESCE(s.sales_value, 0) AS salesValue,
               COALESCE(m.stock_entries, 0) AS stockEntries,
               COALESCE(m.stock_outputs, 0) AS stockOutputs
          FROM tb_product p
          LEFT JOIN tb_stock_balance b ON b.product_id = p.id
          LEFT JOIN sales s ON s.product_id = p.id
          LEFT JOIN movements m ON m.product_id = p.id
         WHERE p.active = true
         ORDER BY p.name ASC, p.code ASC
        """, nativeQuery = true)
    java.util.List<net.ddns.lexdev.systempro_api.repository.projection.StockReportProjection> stockReport(
        @Param("start") java.time.Instant start,
        @Param("endExclusive") java.time.Instant endExclusive
    );

}