package net.ddns.lexdev.systempro_api.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import net.ddns.lexdev.systempro_api.enums.StockMovementOrigin;
import net.ddns.lexdev.systempro_api.enums.StockMovementType;

@Entity
@Table(name = "tb_stock_movement")
@EntityListeners(AuditingEntityListener.class)
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_stock_movement_product"))
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 40)
    private StockMovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 40)
    private StockMovementOrigin origin;

    @Column(name = "source_reference", nullable = false, length = 120)
    private String sourceReference;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "previous_balance", nullable = false, precision = 19, scale = 6)
    private BigDecimal previousBalance;

    @Column(name = "resulting_balance", nullable = false, precision = 19, scale = 6)
    private BigDecimal resultingBalance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", foreignKey = @ForeignKey(name = "fk_stock_movement_sale"))
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_item_id", foreignKey = @ForeignKey(name = "fk_stock_movement_sale_item"))
    private SaleItem saleItem;

    @Column(name = "reason", length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false,
        foreignKey = @ForeignKey(name = "fk_stock_movement_created_by"))
    private User createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(
        Product product,
        StockMovementType movementType,
        StockMovementOrigin origin,
        String sourceReference,
        BigDecimal quantity,
        BigDecimal previousBalance,
        BigDecimal resultingBalance,
        Sale sale,
        SaleItem saleItem,
        String reason,
        User createdBy
    ) {
        this.product = product;
        this.movementType = movementType;
        this.origin = origin;
        this.sourceReference = sourceReference;
        this.quantity = quantity;
        this.previousBalance = previousBalance;
        this.resultingBalance = resultingBalance;
        this.sale = sale;
        this.saleItem = saleItem;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public StockMovementType getMovementType() { return movementType; }
    public StockMovementOrigin getOrigin() { return origin; }
    public String getSourceReference() { return sourceReference; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPreviousBalance() { return previousBalance; }
    public BigDecimal getResultingBalance() { return resultingBalance; }
    public Sale getSale() { return sale; }
    public SaleItem getSaleItem() { return saleItem; }
    public String getReason() { return reason; }
    public User getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}