package net.ddns.lexdev.systempro_api.domain;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.FiscalEventType;

@Entity
@Table(name = "tb_fiscal_event")
public class FiscalEvent extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fiscal_event_document")) private FiscalDocument document;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 30) private FiscalEventType eventType;
    @Column(name = "sequence_number", nullable = false) private int sequenceNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private FiscalDocumentStatus status;
    @Column(name = "xml", columnDefinition = "TEXT") private String xml;
    @Column(name = "response_xml", columnDefinition = "TEXT") private String responseXml;
    @Column(length = 30) private String protocol;
    @Column(length = 1000) private String reason;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt = Instant.now();
    public FiscalDocument getDocument() { return document; } public void setDocument(FiscalDocument v) { document = v; }
    public FiscalEventType getEventType() { return eventType; } public void setEventType(FiscalEventType v) { eventType = v; }
    public int getSequenceNumber() { return sequenceNumber; } public void setSequenceNumber(int v) { sequenceNumber = v; }
    public FiscalDocumentStatus getStatus() { return status; } public void setStatus(FiscalDocumentStatus v) { status = v; }
    public String getXml() { return xml; } public void setXml(String v) { xml = v; }
    public String getResponseXml() { return responseXml; } public void setResponseXml(String v) { responseXml = v; }
    public String getProtocol() { return protocol; } public void setProtocol(String v) { protocol = v; }
    public String getReason() { return reason; } public void setReason(String v) { reason = v; }
    public Instant getOccurredAt() { return occurredAt; } public void setOccurredAt(Instant v) { occurredAt = v; }
}
