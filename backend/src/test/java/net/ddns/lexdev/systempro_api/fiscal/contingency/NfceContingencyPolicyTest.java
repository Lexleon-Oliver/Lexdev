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

    @Test
    void bloqueiaEntradaEmContingenciaSeHouverQualquerEvidenciaDeTransmissao() {
        java.util.List<java.util.function.Consumer<FiscalDocument>> evidencias = java.util.List.of(
            d -> d.setXml("<NFe/>"),
            d -> d.setResponseXml("<retEnviNFe/>"),
            d -> d.setReceiptNumber("123456789012345"),
            d -> d.setProtocol("131260000000001"),
            d -> d.setIssuedAt(Instant.parse("2026-10-08T15:00:00Z"))
        );

        for (java.util.function.Consumer<FiscalDocument> evidencia : evidencias) {
            FiscalDocument document = new FiscalDocument();
            document.setStatus(FiscalDocumentStatus.AGUARDANDO_AUTORIZACAO);
            document.setEmissionType(FiscalEmissionType.NORMAL);
            evidencia.accept(document);
            assertThrows(BusinessException.class, () -> policy.assertCanEnterOfflineContingency(document));
        }
    }

    @Test
    void bloqueiaRetransmissaoQuandoContingenciaJaSaiuDoEstadoContingencia() {
        for (FiscalDocumentStatus status : java.util.List.of(
            FiscalDocumentStatus.PENDENTE_CONSULTA,
            FiscalDocumentStatus.AUTORIZADA,
            FiscalDocumentStatus.REJEITADA,
            FiscalDocumentStatus.CANCELADA
        )) {
            FiscalDocument document = offlineCompleto();
            document.setStatus(status);
            assertThrows(BusinessException.class, () -> policy.assertCanTransmitOfflineContingency(document));
        }
    }

    @Test
    void bloqueiaTransmissaoSeDocumentoOfflinePersistidoEstiverIncompleto() {
        FiscalDocument semChave = offlineCompleto();
        semChave.setAccessKey(null);
        assertThrows(BusinessException.class, () -> policy.assertCanTransmitOfflineContingency(semChave));

        FiscalDocument semXml = offlineCompleto();
        semXml.setXml(null);
        assertThrows(BusinessException.class, () -> policy.assertCanTransmitOfflineContingency(semXml));

        FiscalDocument semData = offlineCompleto();
        semData.setContingencyAt(null);
        assertThrows(BusinessException.class, () -> policy.assertCanTransmitOfflineContingency(semData));

        FiscalDocument semJustificativa = offlineCompleto();
        semJustificativa.setContingencyJustification(" ");
        assertThrows(BusinessException.class, () -> policy.assertCanTransmitOfflineContingency(semJustificativa));
    }

    private FiscalDocument offlineCompleto() {
        FiscalDocument document = new FiscalDocument();
        document.setStatus(FiscalDocumentStatus.CONTINGENCIA);
        document.setEmissionType(FiscalEmissionType.CONTINGENCIA_OFFLINE);
        document.setAccessKey("31261012345678000190650010000000019000000014");
        document.setXml("<NFe/> ");
        document.setContingencyAt(Instant.parse("2026-10-08T15:00:00Z"));
        document.setContingencyJustification("Indisponibilidade temporaria de comunicacao com a SEFAZ.");
        return document;
    }

}