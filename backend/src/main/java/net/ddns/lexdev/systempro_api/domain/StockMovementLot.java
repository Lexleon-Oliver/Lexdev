package net.ddns.lexdev.systempro_api.domain;
import java.math.BigDecimal;
import jakarta.persistence.*;
@Entity
@Table(name="tb_stock_movement_lot", uniqueConstraints=@UniqueConstraint(name="uk_stock_movement_lot", columnNames={"stock_movement_id","stock_lot_id"}))
public class StockMovementLot {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="stock_movement_id", nullable=false) private StockMovement movement;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="stock_lot_id", nullable=false) private StockLot lot;
 @Column(nullable=false, precision=19, scale=6) private BigDecimal quantity;
 protected StockMovementLot(){}
 public StockMovementLot(StockMovement movement, StockLot lot, BigDecimal quantity){this.movement=movement;this.lot=lot;this.quantity=quantity;}
 public StockMovement getMovement(){return movement;} public StockLot getLot(){return lot;} public BigDecimal getQuantity(){return quantity;}
}