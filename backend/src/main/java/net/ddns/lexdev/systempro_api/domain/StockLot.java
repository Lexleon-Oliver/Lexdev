package net.ddns.lexdev.systempro_api.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.*;

@Entity
@Table(name = "tb_stock_lot", uniqueConstraints = @UniqueConstraint(name = "uk_stock_lot_product_code_expiration", columnNames = {"product_id", "lot_code", "expiration_date"}))
public class StockLot extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_stock_lot_product"))
    private Product product;
    @Column(name = "lot_code", nullable = false, length = 100) private String lotCode;
    @Column(name = "manufacture_date") private LocalDate manufactureDate;
    @Column(name = "expiration_date", nullable = false) private LocalDate expirationDate;
    @Column(name = "quantity", nullable = false, precision = 19, scale = 6) private BigDecimal quantity = BigDecimal.ZERO;
    protected StockLot() {}
    public StockLot(Product product, String lotCode, LocalDate manufactureDate, LocalDate expirationDate) {
        this.product=product; this.lotCode=lotCode; this.manufactureDate=manufactureDate; this.expirationDate=expirationDate;
    }
    public Product getProduct(){return product;} public String getLotCode(){return lotCode;} public LocalDate getManufactureDate(){return manufactureDate;} public LocalDate getExpirationDate(){return expirationDate;} public BigDecimal getQuantity(){return quantity;} public void setQuantity(BigDecimal q){quantity=q;}
}