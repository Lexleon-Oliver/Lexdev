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

    @Column(name = "rate_type", length = 80)
    private String rateType;

    @Column(name = "ind_g_ibs_cbs", nullable = false)
    private boolean ibsCbsGroup;

    @Column(name = "ind_g_ibs_cbs_mono", nullable = false)
    private boolean ibsCbsMonoGroup;

    @Column(name = "ind_g_reduction", nullable = false)
    private boolean reductionGroup;

    @Column(name = "ind_g_deferral", nullable = false)
    private boolean deferralGroup;

    @Column(name = "ind_g_transfer_credit", nullable = false)
    private boolean transferCreditGroup;

    @Column(name = "ind_g_presumed_credit_zfm", nullable = false)
    private boolean presumedCreditZfmGroup;

    @Column(name = "ind_g_competence_adjustment", nullable = false)
    private boolean competenceAdjustmentGroup;

    @Column(name = "ind_base_reducer", nullable = false)
    private boolean baseReducer;

    @Column(name = "ind_regular_taxation", nullable = false)
    private boolean regularTaxationGroup;

    @Column(name = "ind_presumed_credit_operation", nullable = false)
    private boolean presumedCreditOperationGroup;

    @Column(name = "ind_mono_standard", nullable = false)
    private boolean monoStandardGroup;

    @Column(name = "ind_mono_withholding", nullable = false)
    private boolean monoWithholdingGroup;

    @Column(name = "ind_mono_withheld", nullable = false)
    private boolean monoWithheldGroup;

    @Column(name = "ind_biofuel_difference", nullable = false)
    private boolean biofuelDifferenceGroup;

    @Column(name = "ind_credit_reversal", nullable = false)
    private boolean creditReversalGroup;

    @Column(name = "gross_revenue_type")
    private Integer grossRevenueType;

    @Column(name = "donation_type")
    private Integer donationType;

    @Column(name = "source_updated_at")
    private LocalDate sourceUpdatedAt;

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
    public String getRateType() { return rateType; }
    public boolean isIbsCbsGroup() { return ibsCbsGroup; }
    public boolean isIbsCbsMonoGroup() { return ibsCbsMonoGroup; }
    public boolean isReductionGroup() { return reductionGroup; }
    public boolean isDeferralGroup() { return deferralGroup; }
    public boolean isTransferCreditGroup() { return transferCreditGroup; }
    public boolean isPresumedCreditZfmGroup() { return presumedCreditZfmGroup; }
    public boolean isCompetenceAdjustmentGroup() { return competenceAdjustmentGroup; }
    public boolean isBaseReducer() { return baseReducer; }
    public boolean isRegularTaxationGroup() { return regularTaxationGroup; }
    public boolean isPresumedCreditOperationGroup() { return presumedCreditOperationGroup; }
    public boolean isMonoStandardGroup() { return monoStandardGroup; }
    public boolean isMonoWithholdingGroup() { return monoWithholdingGroup; }
    public boolean isMonoWithheldGroup() { return monoWithheldGroup; }
    public boolean isBiofuelDifferenceGroup() { return biofuelDifferenceGroup; }
    public boolean isCreditReversalGroup() { return creditReversalGroup; }
    public Integer getGrossRevenueType() { return grossRevenueType; }
    public Integer getDonationType() { return donationType; }
    public LocalDate getSourceUpdatedAt() { return sourceUpdatedAt; }
}