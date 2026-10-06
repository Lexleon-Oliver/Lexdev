package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.ddns.lexdev.systempro_api.domain.FiscalDocument;

public interface FiscalDocumentRepository extends JpaRepository<FiscalDocument, Long> {
    Optional<FiscalDocument> findBySaleId(Long saleId);
    Optional<FiscalDocument> findByAccessKey(String accessKey);

    @Query("SELECT COALESCE(MAX(d.number), 0) FROM FiscalDocument d WHERE d.establishment.id = :establishmentId AND d.series = :series")
    long findMaxNumber(@Param("establishmentId") Long establishmentId, @Param("series") int series);
}
