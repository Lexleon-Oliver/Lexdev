package net.ddns.lexdev.systempro_api.repository;
import java.time.LocalDate; import java.util.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import jakarta.persistence.LockModeType; import net.ddns.lexdev.systempro_api.domain.StockLot;
public interface StockLotRepository extends JpaRepository<StockLot,Long>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select l from StockLot l where l.product.id=:productId and l.quantity > 0 order by l.expirationDate asc, l.id asc") List<StockLot> findAvailableForUpdateFefo(@Param("productId") Long productId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select l from StockLot l where l.product.id=:productId and l.quantity > 0 and l.expirationDate >= current_date order by l.expirationDate asc, l.id asc") List<StockLot> findSellableForUpdateFefo(@Param("productId") Long productId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select l from StockLot l where l.id=:id") Optional<StockLot> findByIdForUpdate(@Param("id") Long id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select l from StockLot l where l.product.id=:productId and l.lotCode=:lotCode and l.expirationDate=:expirationDate") Optional<StockLot> findForUpdate(@Param("productId") Long productId,@Param("lotCode") String lotCode,@Param("expirationDate") LocalDate expirationDate);
 List<StockLot> findByProductIdOrderByExpirationDateAscIdAsc(Long productId);
 boolean existsByProductIdAndQuantityGreaterThan(Long productId, java.math.BigDecimal quantity);
}