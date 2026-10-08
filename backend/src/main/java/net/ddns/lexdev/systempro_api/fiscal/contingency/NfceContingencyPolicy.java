package net.ddns.lexdev.systempro_api.fiscal.contingency;

import org.springframework.stereotype.Component;

import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.FiscalEmissionType;
import net.ddns.lexdev.systempro_api.exception.BusinessException;

@Component
public class NfceContingencyPolicy {

    public void assertCanEnterOfflineContingency(FiscalDocument document) {
        if (document == null) {
            throw new BusinessException("O documento fiscal é obrigatório para iniciar a contingência.");
        }
        if (document.getEmissionType() == FiscalEmissionType.CONTINGENCIA_OFFLINE) {
            throw new BusinessException("A NFC-e já está configurada para contingência offline.");
        }
        if (document.getStatus() != FiscalDocumentStatus.AGUARDANDO_AUTORIZACAO) {
            throw new BusinessException("Somente uma NFC-e ainda não transmitida pode entrar em contingência offline.");
        }
        if (hasTransmissionEvidence(document)) {
            throw new BusinessException(
                "A NFC-e já possui evidência de preparação ou transmissão normal e não pode reutilizar a mesma numeração em contingência offline."
            );
        }
    }

    public void assertCanTransmitOfflineContingency(FiscalDocument document) {
        if (document == null
            || document.getEmissionType() != FiscalEmissionType.CONTINGENCIA_OFFLINE
            || document.getStatus() != FiscalDocumentStatus.CONTINGENCIA) {
            throw new BusinessException("A NFC-e não está pendente de transmissão como contingência offline.");
        }
        if (document.getAccessKey() == null || !document.getAccessKey().matches("\\d{44}")) {
            throw new BusinessException("A NFC-e em contingência não possui chave de acesso válida persistida.");
        }
        if (document.getXml() == null || document.getXml().isBlank()) {
            throw new BusinessException("A NFC-e em contingência não possui XML assinado persistido.");
        }
        if (document.getContingencyAt() == null || document.getContingencyJustification() == null
            || document.getContingencyJustification().isBlank()) {
            throw new BusinessException("A NFC-e em contingência não possui os dados de entrada em contingência persistidos.");
        }
    }

    private boolean hasTransmissionEvidence(FiscalDocument document) {
        return notBlank(document.getAccessKey())
            || notBlank(document.getXml())
            || notBlank(document.getResponseXml())
            || notBlank(document.getReceiptNumber())
            || notBlank(document.getProtocol())
            || document.getIssuedAt() != null;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
