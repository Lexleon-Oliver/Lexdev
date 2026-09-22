package net.ddns.lexdev.systempro_api.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import net.ddns.lexdev.systempro_api.enums.ProductStatus;

@Entity
@Table(name = "tb_product")
@SQLDelete(sql = """
    UPDATE tb_product
       SET active = false
     WHERE id = ? AND version = ?
    """)
@SQLRestriction("active = true")
public class Product extends AuditableEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "manufacturer_code", length = 100)
    private String manufacturerCode;

    @Column(name = "gtin", length = 14)
    private String gtin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status = ProductStatus.ATIVO;

    @Column(name = "unit_of_measure", nullable = false, length = 10)
    private String unitOfMeasure = "UN";

    @Column(name = "controls_stock", nullable = false)
    private boolean controlsStock = true;

    @Column(name = "sale_price", precision = 19, scale = 4)
    private BigDecimal salePrice;

    @Column(name = "minimum_sale_price", precision = 19, scale = 4)
    private BigDecimal minimumSalePrice;

    @Column(name = "minimum_stock", precision = 19, scale = 4)
    private BigDecimal minimumStock;

    @Column(name = "maximum_stock", precision = 19, scale = 4)
    private BigDecimal maximumStock;

    @Column(name = "reorder_point", precision = 19, scale = 4)
    private BigDecimal reorderPoint;

    @Column(name = "ncm", length = 8)
    private String ncm;

    @Column(name = "cest", length = 7)
    private String cest;

    @Column(name = "origin", length = 1)
    private String origin;

    @Column(name = "gross_weight", precision = 15, scale = 4)
    private BigDecimal grossWeight;

    @Column(name = "net_weight", precision = 15, scale = 4)
    private BigDecimal netWeight;

    @Column(name = "height", precision = 15, scale = 4)
    private BigDecimal height;

    @Column(name = "width", precision = 15, scale = 4)
    private BigDecimal width;

    @Column(name = "length", precision = 15, scale = 4)
    private BigDecimal length;

    @OneToMany(
        mappedBy = "product",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<ProductSupplier> suppliers = new ArrayList<>();

    @OneToMany(
        mappedBy = "product",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<ProductImage> images = new ArrayList<>();

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Product() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getManufacturerCode() {
        return manufacturerCode;
    }

    public void setManufacturerCode(String manufacturerCode) {
        this.manufacturerCode = manufacturerCode;
    }

    public String getGtin() {
        return gtin;
    }

    public void setGtin(String gtin) {
        this.gtin = gtin;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public boolean isControlsStock() {
        return controlsStock;
    }

    public void setControlsStock(boolean controlsStock) {
        this.controlsStock = controlsStock;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.salePrice = salePrice;
    }

    public BigDecimal getMinimumSalePrice() {
        return minimumSalePrice;
    }

    public void setMinimumSalePrice(BigDecimal minimumSalePrice) {
        this.minimumSalePrice = minimumSalePrice;
    }

    public BigDecimal getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(BigDecimal minimumStock) {
        this.minimumStock = minimumStock;
    }

    public BigDecimal getMaximumStock() {
        return maximumStock;
    }

    public void setMaximumStock(BigDecimal maximumStock) {
        this.maximumStock = maximumStock;
    }

    public BigDecimal getReorderPoint() {
        return reorderPoint;
    }

    public void setReorderPoint(BigDecimal reorderPoint) {
        this.reorderPoint = reorderPoint;
    }

    public String getNcm() {
        return ncm;
    }

    public void setNcm(String ncm) {
        this.ncm = ncm;
    }

    public String getCest() {
        return cest;
    }

    public void setCest(String cest) {
        this.cest = cest;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public BigDecimal getGrossWeight() {
        return grossWeight;
    }

    public void setGrossWeight(BigDecimal grossWeight) {
        this.grossWeight = grossWeight;
    }

    public BigDecimal getNetWeight() {
        return netWeight;
    }

    public void setNetWeight(BigDecimal netWeight) {
        this.netWeight = netWeight;
    }

    public BigDecimal getHeight() {
        return height;
    }

    public void setHeight(BigDecimal height) {
        this.height = height;
    }

    public BigDecimal getWidth() {
        return width;
    }

    public void setWidth(BigDecimal width) {
        this.width = width;
    }

    public BigDecimal getLength() {
        return length;
    }

    public void setLength(BigDecimal length) {
        this.length = length;
    }

    public List<ProductSupplier> getSuppliers() {
        return suppliers;
    }

    public List<ProductImage> getImages() {
        return images;
    }

    public void addSupplier(ProductSupplier supplier) {
        suppliers.add(supplier);
        supplier.setProduct(this);
    }

    public void removeSupplier(ProductSupplier supplier) {
        suppliers.remove(supplier);
        supplier.setProduct(null);
    }

    public void addImage(ProductImage image) {
        images.add(image);
        image.setProduct(this);
    }

    public void removeImage(ProductImage image) {
        images.remove(image);
        image.setProduct(null);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

}