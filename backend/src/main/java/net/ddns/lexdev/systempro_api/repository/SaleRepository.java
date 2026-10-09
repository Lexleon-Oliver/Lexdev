package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.repository.projection.FinancialDailyProjection;
import net.ddns.lexdev.systempro_api.repository.projection.FinancialPaymentProjection;
import net.ddns.lexdev.systempro_api.repository.projection.FinancialSummaryProjection;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @Query("""
        SELECT DISTINCT s
        FROM Sale s
        LEFT JOIN FETCH s.client c
        LEFT JOIN FETCH c.person cp
        JOIN FETCH s.user u
        JOIN FETCH s.fiscalEstablishment e
        JOIN FETCH e.company ec
        JOIN FETCH ec.person ep
        LEFT JOIN FETCH ep.legalEntity
        LEFT JOIN FETCH s.items i
        WHERE s.id = :id
        """)
    Optional<Sale> findDetailed(@Param("id") Long id);

    @Query("""
        SELECT s
        FROM Sale s
        JOIN FETCH s.user
        JOIN FETCH s.fiscalEstablishment
        WHERE s.id = :id
        """)
    Optional<Sale> findForFiscal(@Param("id") Long id);

    Page<Sale> findByFiscalEstablishmentId(
        Long establishmentId,
        Pageable pageable
    );

    @Query(
        value = """
            SELECT s
            FROM Sale s
            JOIN FiscalDocument d ON d.sale = s
            WHERE d.status IN :statuses
            """,
        countQuery = """
            SELECT COUNT(s)
            FROM Sale s
            JOIN FiscalDocument d ON d.sale = s
            WHERE d.status IN :statuses
            """
    )
    Page<Sale> findByFiscalDocumentStatusIn(
        @Param("statuses") Set<FiscalDocumentStatus> statuses,
        Pageable pageable
    );

    @Query("""
        SELECT COUNT(s) AS sales,
               COALESCE(SUM(s.subtotal), 0) AS grossSales,
               COALESCE(SUM(s.subtotal - s.total), 0) AS discounts,
               COALESCE(SUM(s.total), 0) AS netSales
        FROM Sale s
        WHERE s.saleAt >= :start AND s.saleAt < :endExclusive
          AND s.status <> net.ddns.lexdev.systempro_api.enums.SaleStatus.CANCELADA
        """)
    FinancialSummaryProjection financialSummary(@Param("start") java.time.Instant start, @Param("endExclusive") java.time.Instant endExclusive);

    @Query("""
        SELECT COUNT(s) FROM Sale s
        WHERE s.saleAt >= :start AND s.saleAt < :endExclusive
          AND s.status = net.ddns.lexdev.systempro_api.enums.SaleStatus.CANCELADA
        """)
    long countCancelled(@Param("start") java.time.Instant start, @Param("endExclusive") java.time.Instant endExclusive);

    @Query(value = """
        WITH payment_by_sale AS (
            SELECT p.sale_id, p.payment_method, SUM(p.amount) AS method_amount
            FROM tb_sale_payment p
            GROUP BY p.sale_id, p.payment_method
        ), paid_by_sale AS (
            SELECT p.sale_id, SUM(p.amount) AS total_paid
            FROM tb_sale_payment p
            GROUP BY p.sale_id
        )
        SELECT ps.payment_method AS "paymentMethod",
               COALESCE(SUM(ps.method_amount - CASE
                   WHEN ps.payment_method = 'DINHEIRO' THEN GREATEST(pb.total_paid - s.total, 0)
                   ELSE 0 END), 0) AS "amount"
        FROM payment_by_sale ps
        JOIN paid_by_sale pb ON pb.sale_id = ps.sale_id
        JOIN tb_sale s ON s.id = ps.sale_id
        WHERE s.sale_at >= :start AND s.sale_at < :endExclusive AND s.status <> 'CANCELADA'
        GROUP BY ps.payment_method
        ORDER BY ps.payment_method
        """, nativeQuery = true)
    java.util.List<FinancialPaymentProjection> financialPayments(@Param("start") java.time.Instant start, @Param("endExclusive") java.time.Instant endExclusive);

    @Query(value = """
        SELECT CAST(s.sale_at AT TIME ZONE 'America/Sao_Paulo' AS date) AS "date",
               COUNT(*) AS "sales",
               COALESCE(SUM(s.total), 0) AS "total"
        FROM tb_sale s
        WHERE s.sale_at >= :start AND s.sale_at < :endExclusive AND s.status <> 'CANCELADA'
        GROUP BY CAST(s.sale_at AT TIME ZONE 'America/Sao_Paulo' AS date)
        ORDER BY CAST(s.sale_at AT TIME ZONE 'America/Sao_Paulo' AS date)
        """, nativeQuery = true)
    java.util.List<FinancialDailyProjection> financialDaily(@Param("start") java.time.Instant start, @Param("endExclusive") java.time.Instant endExclusive);

}