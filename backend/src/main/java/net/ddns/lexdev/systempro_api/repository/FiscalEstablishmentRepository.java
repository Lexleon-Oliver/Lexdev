package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;

public interface FiscalEstablishmentRepository extends JpaRepository<FiscalEstablishment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM FiscalEstablishment e JOIN FETCH e.person p LEFT JOIN FETCH p.legalEntity LEFT JOIN FETCH p.addresses WHERE e.id = :id")
    Optional<FiscalEstablishment> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT e FROM FiscalEstablishment e JOIN FETCH e.person p LEFT JOIN FETCH p.legalEntity LEFT JOIN FETCH p.addresses WHERE e.id = :id")
    Optional<FiscalEstablishment> findDetailed(@Param("id") Long id);

    boolean existsByPersonId(Long personId);
}
