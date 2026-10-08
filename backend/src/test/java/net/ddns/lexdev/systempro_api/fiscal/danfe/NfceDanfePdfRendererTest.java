package net.ddns.lexdev.systempro_api.fiscal.danfe;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.domain.FiscalDocument;

class NfceDanfePdfRendererTest {
    private final NfceDanfePdfRenderer renderer = new NfceDanfePdfRenderer();

    @Test
    void deveGerarPdfValidoComDadosFiscaisEQrCode() throws Exception {
        FiscalDocument document = new FiscalDocument();
        document.setAccessKey("31261012345678000190650010000001231123456780");
        document.setProtocol("131260000000001");
        document.setXml(xml());

        byte[] pdf = renderer.render(document);

        assertTrue(pdf.length > 1000);
        assertArrayEquals("%PDF".getBytes(), java.util.Arrays.copyOf(pdf, 4));
        try (var loaded = Loader.loadPDF(pdf)) {
            assertEquals(1, loaded.getNumberOfPages());
            assertTrue(loaded.getPage(0).getMediaBox().getWidth() >= 58f * 72f / 25.4f);
        }
    }

    @Test
    void deveExtrairDadosDoXmlFiscalSemDependerDasEntidadesDaVenda() {
        FiscalDocument document = new FiscalDocument();
        document.setAccessKey("31261012345678000190650010000001231123456780");
        document.setProtocol("131260000000001");
        document.setXml(xml());

        var data = renderer.parse(document);

        assertEquals("EMPRESA TESTE LTDA", data.issuerName());
        assertEquals("123.45", data.total().toPlainString());
        assertEquals(1, data.items().size());
        assertEquals("https://portalsped.fazenda.mg.gov.br/portalnfce/sistema/qrcode.xhtml?p=31261012345678000190650010000001231123456780|3|2", data.qrCode());
    }

    private static String xml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <enviNFe xmlns="http://www.portalfiscal.inf.br/nfe" versao="4.00">
              <idLote>1</idLote><indSinc>1</indSinc><NFe><infNFe Id="NFe31261012345678000190650010000001231123456780" versao="4.00">
                <ide><cUF>31</cUF><cNF>12345678</cNF><natOp>VENDA</natOp><mod>65</mod><serie>1</serie><nNF>123</nNF><dhEmi>2026-10-07T20:00:00-03:00</dhEmi></ide>
                <emit><CNPJ>12345678000190</CNPJ><xNome>EMPRESA TESTE LTDA</xNome><enderEmit><xLgr>Rua Teste</xLgr><nro>100</nro><xBairro>Centro</xBairro><xMun>Barbacena</xMun><UF>MG</UF></enderEmit></emit>
                <det nItem="1"><prod><cProd>001</cProd><xProd>PRODUTO TESTE</xProd><uCom>UN</uCom><qCom>1.000000</qCom><vUnCom>123.4500000000</vUnCom><vProd>123.45</vProd></prod></det>
                <total><ICMSTot><vProd>123.45</vProd><vDesc>0.00</vDesc><vNF>123.45</vNF></ICMSTot></total>
                <pag><detPag><tPag>17</tPag><vPag>123.45</vPag></detPag></pag>
              </infNFe><infNFeSupl><qrCode><![CDATA[https://portalsped.fazenda.mg.gov.br/portalnfce/sistema/qrcode.xhtml?p=31261012345678000190650010000001231123456780|3|2]]></qrCode><urlChave>https://hportalsped.fazenda.mg.gov.br/portalnfce</urlChave></infNFeSupl></NFe>
            </enviNFe>
            """;
    }
}
