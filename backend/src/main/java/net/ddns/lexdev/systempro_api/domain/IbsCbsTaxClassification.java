package net.ddns.lexdev.systempro_api.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsTaxationMode;

/**
 * Snapshot local da tabela oficial/evolutiva CST + cClassTrib.
 * Não armazena alíquotas nominais de IBS/CBS nem valores calculados da operação.
 */
@Entity
@Table(name = "tb_ibs_cbs_tax_classification")
public class IbsCbsTaxClassification {

    @Id
    @Column(name = "c_class_trib", length = 6, nullable = false)
    private String cClassTrib;

    @Column(name = "cst", length = 3, nullable = false)
    private String cst;

    @Column(name = "description", length = 500, nullable = false)
    private String description;

    @Column(name = "taxation_mode", length = 40, nullable = false)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private IbsCbsTaxationMode taxationMode;

    @Column(name = "nfce_allowed", nullable = false)
    private boolean nfceAllowed;

    @Column(name = "ibs_reduction_percent", precision = 7, scale = 4)
    private BigDecimal ibsReductionPercent;

    @Column(name = "cbs_reduction_percent", precision = 7, scale = 4)
    private BigDecimal cbsReductionPercent;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "source_version", length = 40, nullable = false)
    private String sourceVersion;

    protected IbsCbsTaxClassification() {}

    public IbsCbsTaxClassification(String cClassTrib, String cst, String description,
            IbsCbsTaxationMode taxationMode, boolean nfceAllowed,
            BigDecimal ibsReductionPercent, BigDecimal cbsReductionPercent,
            LocalDate validFrom, LocalDate validTo, String sourceVersion) {
        this.cClassTrib = cClassTrib;
        this.cst = cst;
        this.description = description;
        this.taxationMode = taxationMode;
        this.nfceAllowed = nfceAllowed;
        this.ibsReductionPercent = ibsReductionPercent;
        this.cbsReductionPercent = cbsReductionPercent;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.sourceVersion = sourceVersion;
    }

    public String getCClassTrib() { return cClassTrib; }
    public String getCst() { return cst; }
    public String getDescription() { return description; }
    public IbsCbsTaxationMode getTaxationMode() { return taxationMode; }
    public boolean isNfceAllowed() { return nfceAllowed; }
    public BigDecimal getIbsReductionPercent() { return ibsReductionPercent; }
    public BigDecimal getCbsReductionPercent() { return cbsReductionPercent; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public String getSourceVersion() { return sourceVersion; }
}
