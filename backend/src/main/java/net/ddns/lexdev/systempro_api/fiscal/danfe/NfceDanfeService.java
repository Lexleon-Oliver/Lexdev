package net.ddns.lexdev.systempro_api.fiscal.danfe;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.FiscalEmissionType;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.FiscalDocumentRepository;

@Service
public class NfceDanfeService {
    private final FiscalDocumentRepository repository;
    private final NfceDanfePdfRenderer renderer;

    public NfceDanfeService(FiscalDocumentRepository repository, NfceDanfePdfRenderer renderer) {
        this.repository = repository;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public byte[] generate(Long saleId) {
        FiscalDocument document = repository.findBySaleId(saleId)
            .orElseThrow(() -> new EntityNotFoundException("Documento fiscal não encontrado."));

        boolean authorized = document.getStatus() == FiscalDocumentStatus.AUTORIZADA;
        boolean offlineContingency = document.getStatus() == FiscalDocumentStatus.CONTINGENCIA
            && document.getEmissionType() == FiscalEmissionType.CONTINGENCIA_OFFLINE;

        if (!authorized && !offlineContingency) {
            throw new BusinessException("O DANFE NFC-e somente pode ser gerado para documento autorizado ou emitido em contingência offline.");
        }
        if (document.getXml() == null || document.getXml().isBlank()) {
            throw new BusinessException("O XML autorizado da NFC-e não está disponível para gerar o DANFE.");
        }
        if (document.getAccessKey() == null || !document.getAccessKey().matches("\\d{44}")) {
            throw new BusinessException("A chave de acesso da NFC-e não está disponível para gerar o DANFE.");
        }
        if (authorized && (document.getProtocol() == null || document.getProtocol().isBlank())) {
            throw new BusinessException("O protocolo de autorização da NFC-e não está disponível para gerar o DANFE.");
        }
        if (offlineContingency && (document.getContingencyAt() == null
            || document.getContingencyJustification() == null
            || document.getContingencyJustification().isBlank())) {
            throw new BusinessException("Os dados da contingência offline não estão completos para gerar o DANFE.");
        }

        return renderer.render(document);
    }
}