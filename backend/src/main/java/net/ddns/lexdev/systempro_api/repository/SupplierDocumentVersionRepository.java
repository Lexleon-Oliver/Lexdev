package net.ddns.lexdev.systempro_api.repository;

import net.ddns.lexdev.systempro_api.domain.DocumentScanStatus;
import net.ddns.lexdev.systempro_api.domain.SupplierDocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupplierDocumentVersionRepository
    extends JpaRepository<SupplierDocumentVersion, Long> {

    @Query("""
        select coalesce(max(v.versionNumber), 0)
        from SupplierDocumentVersion v
        where v.document.id = :documentId
    """)
    int findMaxVersionNumber(@Param("documentId") Long documentId);

    @Query("""
        select v
        from SupplierDocumentVersion v
        join fetch v.document d
        where v.id = :versionId
          and v.scanStatus = :cleanStatus
          and d.id = :documentId
          and d.supplier.id = :supplierId
    """)
    Optional<SupplierDocumentVersion> findForDownload(
        @Param("supplierId") Long supplierId,
        @Param("documentId") Long documentId,
        @Param("versionId") Long versionId,
        @Param("cleanStatus") DocumentScanStatus cleanStatus
    );

    @Query("""
        select v
        from SupplierDocumentVersion v
        join fetch v.document d
        where d.id = :documentId
          and d.supplier.id = :supplierId
          and v.versionNumber = (
              select max(v2.versionNumber)
              from SupplierDocumentVersion v2
              where v2.document.id = d.id
          )
    """)
    Optional<SupplierDocumentVersion> findLatest(
        @Param("supplierId") Long supplierId,
        @Param("documentId") Long documentId
    );
}
