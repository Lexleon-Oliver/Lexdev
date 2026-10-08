package net.ddns.lexdev.systempro_api.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.ddns.lexdev.systempro_api.domain.IbsCbsTaxClassification;

public interface IbsCbsTaxClassificationRepository extends JpaRepository<IbsCbsTaxClassification, String> {

    @Query("""
        SELECT c
          FROM IbsCbsTaxClassification c
         WHERE c.cClassTrib = :cClassTrib
           AND c.cst = :cst
           AND c.validFrom <= :operationDate
           AND (c.validTo IS NULL OR c.validTo >= :operationDate)
        """)
    Optional<IbsCbsTaxClassification> findApplicable(
        @Param("cst") String cst,
        @Param("cClassTrib") String cClassTrib,
        @Param("operationDate") LocalDate operationDate
    );
}
