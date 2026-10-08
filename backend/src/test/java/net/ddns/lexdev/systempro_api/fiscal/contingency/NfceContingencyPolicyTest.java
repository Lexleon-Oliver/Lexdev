package net.ddns.lexdev.systempro_api.fiscal.contingency;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.FiscalEmissionType;
import net.ddns.lexdev.systempro_api.exception.BusinessException;

class NfceContingencyPolicyTest {
    private final NfceContingencyPolicy policy = new NfceContingencyPolicy();

    @Test
    void permiteContingenciaSomenteAntesDeQualquerEvidenciaDeTransmissaoNormal() {
        FiscalDocument document = new FiscalDocument();
        document.setStatus(FiscalDocumentStatus.AGUARDANDO_AUTORIZACAO);
        document.setEmissionType(FiscalEmissionType.NORMAL);
        assertDoesNotThrow(() -> policy.assertCanEnterOfflineContingency(document));
    }

    @Test
    void bloqueiaContingenciaSeDocumentoNormalJaPossuiChaveOuXml() {
        FiscalDocument document = new FiscalDocument();
        document.setStatus(FiscalDocumentStatus.AGUARDANDO_AUTORIZACAO);
        document.setEmissionType(FiscalEmissionType.NORMAL);
        document.setAccessKey("31261012345678000190650010000000011000000019");
        assertThrows(BusinessException.class, () -> policy.assertCanEnterOfflineContingency(document));
    }

    @Test
    void bloqueiaConversaoDePendenteConsultaParaContingencia() {
        FiscalDocument document = new FiscalDocument();
        document.setStatus(FiscalDocumentStatus.PENDENTE_CONSULTA);
        document.setEmissionType(FiscalEmissionType.NORMAL);
        assertThrows(BusinessException.class, () -> policy.assertCanEnterOfflineContingency(document));
    }

    @Test
    void permiteTransmissaoPosteriorSomenteComDocumentoOfflineCompletoPersistido() {
        FiscalDocument document = new FiscalDocument();
        document.setStatus(FiscalDocumentStatus.CONTINGENCIA);
        document.setEmissionType(FiscalEmissionType.CONTINGENCIA_OFFLINE);
        document.setAccessKey("31261012345678000190650010000000019000000014");
        document.setXml("<NFe/>");
        document.setContingencyAt(Instant.now());
        document.setContingencyJustification("Indisponibilidade temporaria de comunicacao com a SEFAZ.");
        assertDoesNotThrow(() -> policy.assertCanTransmitOfflineContingency(document));
    }
}
