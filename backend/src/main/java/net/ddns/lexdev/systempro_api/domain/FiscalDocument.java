package net.ddns.lexdev.systempro_api.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.FiscalEmissionType;

@Entity
@Table(name = "tb_fiscal_document")
public class FiscalDocument extends AuditableEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_fiscal_document_sale"))
    private Sale sale;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fiscal_document_establishment"))
    private FiscalEstablishment establishment;
    @Column(nullable = false, length = 2) private String model = "65";
    @Column(nullable = false) private int series;
    @Column(nullable = false) private long number;
    @Column(name = "access_key", length = 44, unique = true) private String accessKey;
    @Enumerated(EnumType.STRING) @Column(name = "emission_type", nullable = false, length = 30) private FiscalEmissionType emissionType = FiscalEmissionType.NORMAL;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private FiscalDocumentStatus status = FiscalDocumentStatus.AGUARDANDO_AUTORIZACAO;
    @Column(name = "xml", columnDefinition = "TEXT") private String xml;
    @Column(name = "response_xml", columnDefinition = "TEXT") private String responseXml;
    @Column(name = "receipt_number", length = 30) private String receiptNumber;
    @Column(name = "protocol", length = 30) private String protocol;
    @Column(name = "reason", length = 1000) private String reason;
    @Column(name = "issued_at") private Instant issuedAt;
    @Column(name = "contingency_at") private Instant contingencyAt;
    @Column(name = "contingency_justification", length = 256) private String contingencyJustification;
    @Column(name = "canceled_at") private Instant canceledAt;
    @Column(name = "cancellation_protocol", length = 30) private String cancellationProtocol;
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true) private List<FiscalEvent> events = new ArrayList<>();
    public Sale getSale() { return sale; } public void setSale(Sale v) { sale = v; }
    public FiscalEstablishment getEstablishment() { return establishment; } public void setEstablishment(FiscalEstablishment v) { establishment = v; }
    public String getModel() { return model; } public void setModel(String v) { model = v; }
    public int getSeries() { return series; } public void setSeries(int v) { series = v; }
    public long getNumber() { return number; } public void setNumber(long v) { number = v; }
    public String getAccessKey() { return accessKey; } public void setAccessKey(String v) { accessKey = v; }
    public FiscalEmissionType getEmissionType() { return emissionType; } public void setEmissionType(FiscalEmissionType v) { emissionType = v; }
    public FiscalDocumentStatus getStatus() { return status; } public void setStatus(FiscalDocumentStatus v) { status = v; }
    public String getXml() { return xml; } public void setXml(String v) { xml = v; }
    public String getResponseXml() { return responseXml; } public void setResponseXml(String v) { responseXml = v; }
    public String getReceiptNumber() { return receiptNumber; } public void setReceiptNumber(String v) { receiptNumber = v; }
    public String getProtocol() { return protocol; } public void setProtocol(String v) { protocol = v; }
    public String getReason() { return reason; } public void setReason(String v) { reason = v; }
    public Instant getIssuedAt() { return issuedAt; } public void setIssuedAt(Instant v) { issuedAt = v; }
    public Instant getContingencyAt() { return contingencyAt; } public void setContingencyAt(Instant v) { contingencyAt = v; }
    public String getContingencyJustification() { return contingencyJustification; } public void setContingencyJustification(String v) { contingencyJustification = v; }
    public Instant getCanceledAt() { return canceledAt; } public void setCanceledAt(Instant v) { canceledAt = v; }
    public String getCancellationProtocol() { return cancellationProtocol; } public void setCancellationProtocol(String v) { cancellationProtocol = v; }
    public List<FiscalEvent> getEvents() { return events; }
}