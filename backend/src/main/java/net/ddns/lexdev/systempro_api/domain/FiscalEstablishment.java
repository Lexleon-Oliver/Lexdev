package net.ddns.lexdev.systempro_api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import net.ddns.lexdev.systempro_api.enums.FiscalEnvironment;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;

@Entity
@Table(name = "tb_fiscal_establishment")
public class FiscalEstablishment extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "company_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_fiscal_establishment_company")
    )
    private Company company;

    @Column(name = "municipality_ibge_code", nullable = false, length = 7)
    private String municipalityIbgeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_regime", nullable = false, length = 30)
    private TaxRegime taxRegime;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 20)
    private FiscalEnvironment environment = FiscalEnvironment.HOMOLOGACAO;

    @Column(nullable = false)
    private int series = 1;

    @Column(name = "next_number", nullable = false)
    private long nextNumber = 1L;

    @Column(name = "certificate_storage_key", length = 500)
    private String certificateStorageKey;

    @Column(name = "encrypted_certificate_password", columnDefinition = "TEXT")
    private String encryptedCertificatePassword;

    @Column(name = "csc_id")
    private Integer cscId;

    @Column(name = "encrypted_csc", columnDefinition = "TEXT")
    private String encryptedCsc;

    @Column(nullable = false)
    private boolean active = true;

    protected FiscalEstablishment() {
    }

    public FiscalEstablishment(Company company) {
        this.company = company;
    }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getMunicipalityIbgeCode() { return municipalityIbgeCode; }
    public void setMunicipalityIbgeCode(String value) { this.municipalityIbgeCode = value; }
    public TaxRegime getTaxRegime() { return taxRegime; }
    public void setTaxRegime(TaxRegime value) { this.taxRegime = value; }
    public FiscalEnvironment getEnvironment() { return environment; }
    public void setEnvironment(FiscalEnvironment value) { this.environment = value; }
    public int getSeries() { return series; }
    public void setSeries(int value) { this.series = value; }
    public long getNextNumber() { return nextNumber; }
    public void setNextNumber(long value) { this.nextNumber = value; }
    public String getCertificateStorageKey() { return certificateStorageKey; }
    public void setCertificateStorageKey(String value) { this.certificateStorageKey = value; }
    public String getEncryptedCertificatePassword() { return encryptedCertificatePassword; }
    public void setEncryptedCertificatePassword(String value) { this.encryptedCertificatePassword = value; }
    public Integer getCscId() { return cscId; }
    public void setCscId(Integer value) { this.cscId = value; }
    public String getEncryptedCsc() { return encryptedCsc; }
    public void setEncryptedCsc(String value) { this.encryptedCsc = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { this.active = value; }
    public String cnpj() { return company.getPerson().getCpfCnpj(); }
}
