package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.ddns.lexdev.systempro_api.domain.FiscalProductProfile;

public interface FiscalProductProfileRepository extends JpaRepository<FiscalProductProfile, Long> {
    @Query("SELECT p FROM FiscalProductProfile p JOIN FETCH p.product WHERE p.product.id = :productId")
    Optional<FiscalProductProfile> findByProductId(@Param("productId") Long productId);
}
