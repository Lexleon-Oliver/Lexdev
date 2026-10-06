package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.ddns.lexdev.systempro_api.domain.Sale;

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
}