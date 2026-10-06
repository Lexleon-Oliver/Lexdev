package net.ddns.lexdev.systempro_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.ddns.lexdev.systempro_api.domain.FiscalEvent;
import net.ddns.lexdev.systempro_api.enums.FiscalEventType;

public interface FiscalEventRepository extends JpaRepository<FiscalEvent, Long> {

    @Query("""
        SELECT COALESCE(MAX(e.sequenceNumber), 0)
        FROM FiscalEvent e
        WHERE e.document.id = :documentId
          AND e.eventType = :eventType
        """)
    int findMaxSequenceNumber(
        @Param("documentId") Long documentId,
        @Param("eventType") FiscalEventType eventType
    );
}
