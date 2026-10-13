package net.ddns.lexdev.systempro_api.repository;
import java.util.List; import org.springframework.data.jpa.repository.JpaRepository; import net.ddns.lexdev.systempro_api.domain.StockMovementLot;
public interface StockMovementLotRepository extends JpaRepository<StockMovementLot,Long>{ List<StockMovementLot> findByMovementIdOrderByLotExpirationDateAscLotIdAsc(Long movementId); }