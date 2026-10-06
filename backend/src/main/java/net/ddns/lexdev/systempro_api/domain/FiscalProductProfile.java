package net.ddns.lexdev.systempro_api.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tb_product_fiscal_profile")
public class FiscalProductProfile extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "cfop", nullable = false, length = 4)
    private String cfop;
    @Column(name = "icms_cst_csosn", nullable = false, length = 3)
    private String icmsCstCsosn;
    @Column(name = "pis_cst", nullable = false, length = 2)
    private String pisCst;
    @Column(name = "cofins_cst", nullable = false, length = 2)
    private String cofinsCst;
    @Column(name = "icms_rate", precision = 7, scale = 4)
    private BigDecimal icmsRate;
    @Column(name = "pis_rate", precision = 7, scale = 4)
    private BigDecimal pisRate;
    @Column(name = "cofins_rate", precision = 7, scale = 4)
    private BigDecimal cofinsRate;
    @Column(name = "ibs_cbs_cst", length = 3)
    private String ibsCbsCst;
    @Column(name = "c_class_trib", length = 10)
    private String cClassTrib;
    @Column(name = "ibs_rate", precision = 9, scale = 4)
    private BigDecimal ibsRate;
    @Column(name = "cbs_rate", precision = 9, scale = 4)
    private BigDecimal cbsRate;
    @Column(name = "additional_information", columnDefinition = "TEXT")
    private String additionalInformation;
    @Column(nullable = false)
    private boolean active = true;

    protected FiscalProductProfile() {
    }

    public FiscalProductProfile(Product product) { this.product = product; }
    public Product getProduct() { return product; }
    public void setProduct(Product value) { this.product = value; }
    public String getCfop() { return cfop; }
    public void setCfop(String value) { this.cfop = value; }
    public String getIcmsCstCsosn() { return icmsCstCsosn; }
    public void setIcmsCstCsosn(String value) { this.icmsCstCsosn = value; }
    public String getPisCst() { return pisCst; }
    public void setPisCst(String value) { this.pisCst = value; }
    public String getCofinsCst() { return cofinsCst; }
    public void setCofinsCst(String value) { this.cofinsCst = value; }
    public BigDecimal getIcmsRate() { return icmsRate; }
    public void setIcmsRate(BigDecimal value) { this.icmsRate = value; }
    public BigDecimal getPisRate() { return pisRate; }
    public void setPisRate(BigDecimal value) { this.pisRate = value; }
    public BigDecimal getCofinsRate() { return cofinsRate; }
    public void setCofinsRate(BigDecimal value) { this.cofinsRate = value; }
    public String getIbsCbsCst() { return ibsCbsCst; }
    public void setIbsCbsCst(String value) { this.ibsCbsCst = value; }
    public String getCClassTrib() { return cClassTrib; }
    public void setCClassTrib(String value) { this.cClassTrib = value; }
    public BigDecimal getIbsRate() { return ibsRate; }
    public void setIbsRate(BigDecimal value) { this.ibsRate = value; }
    public BigDecimal getCbsRate() { return cbsRate; }
    public void setCbsRate(BigDecimal value) { this.cbsRate = value; }
    public String getAdditionalInformation() { return additionalInformation; }
    public void setAdditionalInformation(String value) { this.additionalInformation = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { this.active = value; }
}
