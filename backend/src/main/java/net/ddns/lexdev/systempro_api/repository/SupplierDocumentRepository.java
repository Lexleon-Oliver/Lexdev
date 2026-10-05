package net.ddns.lexdev.systempro_api.repository;

import jakarta.persistence.LockModeType;
import net.ddns.lexdev.systempro_api.domain.SupplierDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierDocumentRepository extends JpaRepository<SupplierDocument, Long> {

    @Query("""
        select distinct d
        from SupplierDocument d
        left join fetch d.versions
        where d.supplier.id = :supplierId
        order by d.tipoDocumento asc, d.id asc
    """)
    List<SupplierDocument> findAllWithVersions(
        @Param("supplierId") Long supplierId
    );

    @Query("""
        select distinct d
        from SupplierDocument d
        left join fetch d.versions
        where d.id = :documentId
          and d.supplier.id = :supplierId
    """)
    Optional<SupplierDocument> findDetailed(
        @Param("documentId") Long documentId,
        @Param("supplierId") Long supplierId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select d
        from SupplierDocument d
        where d.id = :documentId
          and d.supplier.id = :supplierId
    """)
    Optional<SupplierDocument> findForUpdate(
        @Param("documentId") Long documentId,
        @Param("supplierId") Long supplierId
    );

    boolean existsByIdAndSupplierId(Long id, Long supplierId);
}
