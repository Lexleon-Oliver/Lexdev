package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.repository.projection.DashboardFiscalProjection;

public interface FiscalDocumentRepository extends JpaRepository<FiscalDocument, Long> {
    Optional<FiscalDocument> findBySaleId(Long saleId);
    Optional<FiscalDocument> findByAccessKey(String accessKey);

    @Query("""
        SELECT
            SUM(CASE WHEN d.status = net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus.AGUARDANDO_AUTORIZACAO THEN 1 ELSE 0 END) AS awaitingAuthorization,
            SUM(CASE WHEN d.status = net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus.PENDENTE_CONSULTA THEN 1 ELSE 0 END) AS pendingConsultation,
            SUM(CASE WHEN d.status = net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus.CONTINGENCIA THEN 1 ELSE 0 END) AS offlineContingency,
            SUM(CASE WHEN d.status = net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus.CANCELAMENTO_PENDENTE THEN 1 ELSE 0 END) AS pendingCancellation
        FROM FiscalDocument d
        """)
    DashboardFiscalProjection dashboardFiscalSummary();

    @Query("SELECT COALESCE(MAX(d.number), 0) FROM FiscalDocument d WHERE d.establishment.id = :establishmentId AND d.series = :series")
    long findMaxNumber(@Param("establishmentId") Long establishmentId, @Param("series") int series);
}