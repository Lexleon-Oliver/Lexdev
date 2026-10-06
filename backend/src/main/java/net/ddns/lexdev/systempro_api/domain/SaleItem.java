package net.ddns.lexdev.systempro_api.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tb_sale_item")
public class SaleItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sale_item_sale"))
    private Sale sale;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sale_item_product"))
    private Product product;
    @Column(name = "item_number", nullable = false) private int itemNumber;
    @Column(name = "code_snapshot", nullable = false, length = 50) private String codeSnapshot;
    @Column(name = "name_snapshot", nullable = false, length = 200) private String nameSnapshot;
    @Column(name = "unit_snapshot", nullable = false, length = 10) private String unitSnapshot;
    @Column(name = "gtin_snapshot", length = 14) private String gtinSnapshot;
    @Column(name = "ncm_snapshot", length = 8) private String ncmSnapshot;
    @Column(name = "cest_snapshot", length = 7) private String cestSnapshot;
    @Column(name = "origin_snapshot", length = 1) private String originSnapshot;
    @Column(nullable = false, precision = 19, scale = 6) private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4) private BigDecimal unitPrice;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal discount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal total = BigDecimal.ZERO;
    @Column(name = "cfop_snapshot", nullable = false, length = 4) private String cfopSnapshot;
    @Column(name = "icms_cst_csosn_snapshot", nullable = false, length = 3) private String icmsCstCsosnSnapshot;
    @Column(name = "pis_cst_snapshot", nullable = false, length = 2) private String pisCstSnapshot;
    @Column(name = "cofins_cst_snapshot", nullable = false, length = 2) private String cofinsCstSnapshot;
    @Column(name = "icms_rate", precision = 7, scale = 4) private BigDecimal icmsRate;
    @Column(name = "pis_rate", precision = 7, scale = 4) private BigDecimal pisRate;
    @Column(name = "cofins_rate", precision = 7, scale = 4) private BigDecimal cofinsRate;

    public Long getId() { return id; }
    public Sale getSale() { return sale; } public void setSale(Sale v) { sale = v; }
    public Product getProduct() { return product; } public void setProduct(Product v) { product = v; }
    public int getItemNumber() { return itemNumber; } public void setItemNumber(int v) { itemNumber = v; }
    public String getCodeSnapshot() { return codeSnapshot; } public void setCodeSnapshot(String v) { codeSnapshot = v; }
    public String getNameSnapshot() { return nameSnapshot; } public void setNameSnapshot(String v) { nameSnapshot = v; }
    public String getUnitSnapshot() { return unitSnapshot; } public void setUnitSnapshot(String v) { unitSnapshot = v; }
    public String getGtinSnapshot() { return gtinSnapshot; } public void setGtinSnapshot(String v) { gtinSnapshot = v; }
    public String getNcmSnapshot() { return ncmSnapshot; } public void setNcmSnapshot(String v) { ncmSnapshot = v; }
    public String getCestSnapshot() { return cestSnapshot; } public void setCestSnapshot(String v) { cestSnapshot = v; }
    public String getOriginSnapshot() { return originSnapshot; } public void setOriginSnapshot(String v) { originSnapshot = v; }
    public BigDecimal getQuantity() { return quantity; } public void setQuantity(BigDecimal v) { quantity = v; }
    public BigDecimal getUnitPrice() { return unitPrice; } public void setUnitPrice(BigDecimal v) { unitPrice = v; }
    public BigDecimal getDiscount() { return discount; } public void setDiscount(BigDecimal v) { discount = v; }
    public BigDecimal getTotal() { return total; } public void setTotal(BigDecimal v) { total = v; }
    public String getCfopSnapshot() { return cfopSnapshot; } public void setCfopSnapshot(String v) { cfopSnapshot = v; }
    public String getIcmsCstCsosnSnapshot() { return icmsCstCsosnSnapshot; } public void setIcmsCstCsosnSnapshot(String v) { icmsCstCsosnSnapshot = v; }
    public String getPisCstSnapshot() { return pisCstSnapshot; } public void setPisCstSnapshot(String v) { pisCstSnapshot = v; }
    public String getCofinsCstSnapshot() { return cofinsCstSnapshot; } public void setCofinsCstSnapshot(String v) { cofinsCstSnapshot = v; }
    public BigDecimal getIcmsRate() { return icmsRate; } public void setIcmsRate(BigDecimal v) { icmsRate = v; }
    public BigDecimal getPisRate() { return pisRate; } public void setPisRate(BigDecimal v) { pisRate = v; }
    public BigDecimal getCofinsRate() { return cofinsRate; } public void setCofinsRate(BigDecimal v) { cofinsRate = v; }
}
