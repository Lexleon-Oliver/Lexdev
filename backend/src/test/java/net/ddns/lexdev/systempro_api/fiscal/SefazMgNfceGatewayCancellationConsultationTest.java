package net.ddns.lexdev.systempro_api.fiscal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;

class SefazMgNfceGatewayCancellationConsultationTest {

    private static final String ACCESS_KEY = "3".repeat(44);

    @Test
    void deveReconhecerCancelamentoConfirmadoNoProcEvento() {
        FiscalDocument document = document();
        String xml = """
            <retConsSitNFe xmlns="http://www.portalfiscal.inf.br/nfe" versao="4.00">
              <tpAmb>2</tpAmb><cStat>101</cStat><xMotivo>Cancelamento de NF-e homologado</xMotivo>
              <chNFe>%s</chNFe>
              <procEventoNFe versao="1.00">
                <evento versao="1.00"><infEvento Id="ID110111%s01">
                  <tpEvento>110111</tpEvento><nSeqEvento>1</nSeqEvento>
                </infEvento></evento>
                <retEvento versao="1.00"><infEvento>
                  <tpAmb>2</tpAmb><verAplic>MG</verAplic><cOrgao>31</cOrgao>
                  <cStat>135</cStat><xMotivo>Evento registrado e vinculado a NF-e</xMotivo>
                  <chNFe>%s</chNFe><tpEvento>110111</tpEvento><nSeqEvento>1</nSeqEvento>
                  <nProt>131260000000999</nProt>
                </infEvento></retEvento>
              </procEventoNFe>
            </retConsSitNFe>
            """.formatted(ACCESS_KEY, ACCESS_KEY, ACCESS_KEY);

        NfceIssueResult result = SefazMgNfceGateway.parseCancellationConsultationResponse(document, xml);

        assertThat(result.status()).isEqualTo(FiscalDocumentStatus.CANCELADA);
        assertThat(result.protocol()).isEqualTo("131260000000999");
        assertThat(result.responseXml()).isEqualTo(xml);
    }

    @Test
    void deveManterPendenteQuandoNotaContinuaAutorizadaSemEventoDeCancelamento() {
        FiscalDocument document = document();
        String xml = """
            <retConsSitNFe xmlns="http://www.portalfiscal.inf.br/nfe" versao="4.00">
              <tpAmb>2</tpAmb><cStat>100</cStat><xMotivo>Autorizado o uso da NF-e</xMotivo>
              <chNFe>%s</chNFe>
              <protNFe><infProt><cStat>100</cStat><xMotivo>Autorizado o uso da NF-e</xMotivo></infProt></protNFe>
            </retConsSitNFe>
            """.formatted(ACCESS_KEY);

        NfceIssueResult result = SefazMgNfceGateway.parseCancellationConsultationResponse(document, xml);

        assertThat(result.status()).isEqualTo(FiscalDocumentStatus.CANCELAMENTO_PENDENTE);
        assertThat(result.protocol()).isNull();
    }

    @Test
    void deveIgnorarEventoQueNaoSejaCancelamento() {
        FiscalDocument document = document();
        String xml = """
            <retConsSitNFe xmlns="http://www.portalfiscal.inf.br/nfe" versao="4.00">
              <cStat>100</cStat><xMotivo>Autorizado o uso da NF-e</xMotivo>
              <procEventoNFe><retEvento><infEvento>
                <cStat>135</cStat><xMotivo>Outro evento</xMotivo><tpEvento>110110</tpEvento>
                <nProt>999</nProt>
              </infEvento></retEvento></procEventoNFe>
            </retConsSitNFe>
            """;

        NfceIssueResult result = SefazMgNfceGateway.parseCancellationConsultationResponse(document, xml);

        assertThat(result.status()).isEqualTo(FiscalDocumentStatus.CANCELAMENTO_PENDENTE);
    }

    private FiscalDocument document() {
        FiscalDocument document = mock(FiscalDocument.class);
        when(document.getAccessKey()).thenReturn(ACCESS_KEY);
        when(document.getXml()).thenReturn("<NFe/>");
        return document;
    }
}