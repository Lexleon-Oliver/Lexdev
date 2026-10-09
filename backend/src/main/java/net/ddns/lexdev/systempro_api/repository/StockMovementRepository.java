package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import net.ddns.lexdev.systempro_api.domain.StockMovement;
import net.ddns.lexdev.systempro_api.enums.StockMovementOrigin;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    boolean existsByOriginAndSourceReference(StockMovementOrigin origin, String sourceReference);

    Optional<StockMovement> findByOriginAndSourceReference(
        StockMovementOrigin origin,
        String sourceReference
    );

    Page<StockMovement> findByProductIdOrderByCreatedAtDescIdDesc(
        Long productId,
        Pageable pageable
    );
}