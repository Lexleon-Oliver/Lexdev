package net.ddns.lexdev.systempro_api.fiscal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsCalculation;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsJurisdictionCalculation;

class NfceTaxXmlReconciliationTest {
    private static BigDecimal bd(String n) { return new BigDecimal(n); }

    @Test
    void conciliaValoresTributadosDoItemETotaisMasNaoInventaInclusaoNoPreco() {
        var report = NfceTaxXmlReconciliation.reconcile(sale(true), TaxRegime.REGIME_NORMAL, xml(true));
        assertEquals(1, report.items().size());
        assertEquals(bd("100.00"), report.grossProduct());
        assertEquals(bd("10.00"), report.discount());
        assertEquals(bd("90.00"), report.netCommercial());
        assertEquals(bd("16.20"), report.icms());
        assertEquals(bd("1.49"), report.pis());
        assertEquals(bd("6.84"), report.cofins());
        assertEquals(bd("0.09"), report.ibsUf());
        assertEquals(bd("0.81"), report.cbs());
        assertTrue(report.rtcPresent());
        assertEquals(NfceTaxXmlReconciliation.PriceInclusionStatus.NOT_DEMONSTRATED,
            report.priceInclusionStatus());
        assertFalse(report.items().get(0).evidence().allReportedAmountsZero());
    }

    @Test
    void auditoriaDeOrigemV3ContinuaBloqueadaAposXmlFiscalConciliado() {
        var report = NfceTaxXmlReconciliation.reconcile(
            sale(true), TaxRegime.REGIME_NORMAL, xml(true));
        var priceAudit = NfcePriceFormationProvenance.assess(report, null);
        assertEquals(NfcePriceFormationProvenance.Status.SOURCE_MISSING, priceAudit.status());
        assertFalse(priceAudit.authorizesLiquidValues());
        assertEquals(64, priceAudit.reconciliationFingerprint().length());
    }

    @Test
    void rejeitaDivergenciaEntrePrecoPersistidoEXml() {
        expectFail(xml(true).replace("<vProd>100.00</vProd>", "<vProd>99.00</vProd>"));
    }

    @Test
    void rejeitaDescontoOmitidoOuAlterado() {
        expectFail(xml(true).replace("<vDesc>10.00</vDesc>", ""));
        expectFail(xml(true).replace("<vDesc>10.00</vDesc>", "<vDesc>9.00</vDesc>"));
    }

    @Test
    void rejeitaQuantidadesOuPrecosUnitariosAlterados() {
        expectFail(xml(true).replace("<qCom>1.0000</qCom>", "<qCom>2.0000</qCom>"));
        expectFail(xml(true).replace("<vUnCom>100.0000000000</vUnCom>",
            "<vUnCom>90.0000000000</vUnCom>"));
    }

    @Test
    void rejeitaIcmsAlteradoNoXml() {
        expectFail(xml(true).replace("<vICMS>16.20</vICMS>", "<vICMS>16.21</vICMS>"));
        expectFail(xml(true).replace("<pICMS>18.0000</pICMS>", "<pICMS>17.0000</pICMS>"));
    }

    @Test
    void rejeitaPisCofinsAlteradosEGrupoCstErrado() {
        expectFail(xml(true).replace("<vPIS>1.49</vPIS>", "<vPIS>1.48</vPIS>"));
        expectFail(xml(true).replace("<vCOFINS>6.84</vCOFINS>", "<vCOFINS>6.83</vCOFINS>"));
        expectFail(xml(true).replace("<PISAliq><CST>01</CST>", "<PISAliq><CST>02</CST>"));
        expectFail(xml(true).replace("<pPIS>1.6500</pPIS>", "<pPIS>1.6600</pPIS>"));
        expectFail(xml(true).replace("<pCOFINS>7.6000</pCOFINS>", "<pCOFINS>7.5000</pCOFINS>"));
    }

    @Test
    void rejeitaRtcAlteradoOuOmitidoSemModificarSnapshot() {
        expectFail(xml(true).replace("<vIBSUF>0.09</vIBSUF>", "<vIBSUF>0.10</vIBSUF>"));
        expectFail(xml(true).replace("<pIBSUF>0.1000</pIBSUF>", "<pIBSUF>0.2000</pIBSUF>"));
        expectFail(xml(true).replace("<cClassTrib>000001</cClassTrib>", "<cClassTrib>000002</cClassTrib>"));
        expectFail(xml(false));
    }

    @Test
    void rejeitaTotaisComDivergenciasDeCentavos() {
        expectFail(xml(true).replace("<vNF>90.00</vNF>", "<vNF>90.01</vNF>"));
        expectFail(xml(true).replace("<vBCIBSCBS>90.00</vBCIBSCBS>", "<vBCIBSCBS>90.01</vBCIBSCBS>"));
        expectFail(xml(true).replace("<vIBS>0.09</vIBS>", "<vIBS>0.10</vIBS>"));
    }

    @Test
    void rejeitaItensFiscaisDuplicadosOuOmitidos() {
        String valid = xml(true);
        String det = valid.substring(valid.indexOf("<det nItem"), valid.indexOf("</det>") + 6);
        expectFail(valid.replace("</infNFe>", det + "</infNFe>"));
        expectFail(valid.replace(det, ""));
    }

    @Test
    void naoAceitaDocumentoComEntidadeExterna() {
        String malicious = "<!DOCTYPE NFe [<!ENTITY bad SYSTEM 'file:///etc/passwd'>]>" + xml(true);
        expectFail(malicious);
    }

    @Test
    void conciliaVendaSemRtcSemConfundirAusenciaComZero() {
        var report = NfceTaxXmlReconciliation.reconcile(sale(false), TaxRegime.REGIME_NORMAL, xml(false));
        assertFalse(report.rtcPresent());
        assertFalse(report.items().get(0).evidence().rtcPresent());
        assertEquals(NfceTaxXmlReconciliation.PriceInclusionStatus.NOT_DEMONSTRATED,
            report.priceInclusionStatus());
    }

    @Test
    void confereSimplesNacionalSemPresumirPrecoLivreDeTributos() {
        Sale sale = sale(false);
        SaleItem item = sale.getItems().get(0);
        item.setIcmsCstCsosnSnapshot("102");
        item.setPisCstSnapshot("06");
        item.setCofinsCstSnapshot("06");
        String simple = xml(false)
            .replace("<ICMS00><CST>00</CST><vBC>90.00</vBC><pICMS>18.0000</pICMS>"
                + "<vICMS>16.20</vICMS></ICMS00>",
                "<ICMSSN102><CSOSN>102</CSOSN></ICMSSN102>")
            .replace("<PISAliq><CST>01</CST><vBC>90.00</vBC><pPIS>1.6500</pPIS>"
                + "<vPIS>1.49</vPIS></PISAliq>", "<PISNT><CST>06</CST></PISNT>")
            .replace("<COFINSAliq><CST>01</CST><vBC>90.00</vBC>"
                + "<pCOFINS>7.6000</pCOFINS><vCOFINS>6.84</vCOFINS></COFINSAliq>",
                "<COFINSNT><CST>06</CST></COFINSNT>")
            .replace("<ICMSTot><vBC>90.00</vBC><vICMS>16.20</vICMS>",
                "<ICMSTot><vBC>0.00</vBC><vICMS>0.00</vICMS>")
            .replace("<vPIS>1.49</vPIS><vCOFINS>6.84</vCOFINS>",
                "<vPIS>0.00</vPIS><vCOFINS>0.00</vCOFINS>");
        var audit = NfceTaxXmlReconciliation.reconcile(sale, TaxRegime.SIMPLES_NACIONAL, simple);
        assertEquals(0, BigDecimal.ZERO.compareTo(audit.icms()));
        assertEquals(NfceTaxXmlReconciliation.PriceInclusionStatus.NOT_DEMONSTRATED,
            audit.priceInclusionStatus());
    }

    @Test
    void rejeitaRtcNoXmlSemOrigemPersistida() {
        assertThrows(FiscalIntegrationException.class,
            () -> NfceTaxXmlReconciliation.reconcile(sale(false), TaxRegime.REGIME_NORMAL, xml(true)));
    }

    @Test
    void permiteSnapshotLiquidoNaoEmitidoQuandoFlagDeEmissaoEstaDesligada() {
        Sale sale = sale(true);
        sale.getItems().get(0).setNfceLiquidSnapshot(
            new NfceLiquidProductValues(bd("65.00"), bd("65.00")), "AUDIT:FIXTURE-V1");
        var report = NfceTaxXmlReconciliation.reconcile(sale, TaxRegime.REGIME_NORMAL, xml(true));
        assertEquals(NfceTaxXmlReconciliation.PriceInclusionStatus.NOT_DEMONSTRATED,
            report.priceInclusionStatus());
    }

    @Test
    void valorLiquidoNoXmlExigeSnapshotCompletoETotalCorrespondente() {
        String withLiquid = xml(true).replace("<vProd>100.00</vProd>",
                "<vProd>100.00</vProd><vUnComLiq>65.0000000000</vUnComLiq><vProdLiq>65.00</vProdLiq>")
            .replace("<ICMSTot>", "<vProdLiqTot>65.00</vProdLiqTot><ICMSTot>");
        assertThrows(FiscalIntegrationException.class,
            () -> NfceTaxXmlReconciliation.reconcile(sale(true), TaxRegime.REGIME_NORMAL, withLiquid));
        Sale sale = sale(true);
        sale.getItems().get(0).setNfceLiquidSnapshot(
            new NfceLiquidProductValues(bd("65.00"), bd("65.00")), "AUDIT:FIXTURE-V1");
        var report = NfceTaxXmlReconciliation.reconcile(sale, TaxRegime.REGIME_NORMAL, withLiquid);
        assertEquals(bd("90.00"), report.netCommercial());
        assertEquals(NfceTaxXmlReconciliation.PriceInclusionStatus.NOT_DEMONSTRATED,
            report.priceInclusionStatus());
        assertThrows(FiscalIntegrationException.class,
            () -> NfceTaxXmlReconciliation.reconcile(sale, TaxRegime.REGIME_NORMAL,
                withLiquid.replace("<vProdLiqTot>65.00</vProdLiqTot>", "<vProdLiqTot>64.99</vProdLiqTot>")));
    }

    private static void expectFail(String xml) {
        assertThrows(FiscalIntegrationException.class,
            () -> NfceTaxXmlReconciliation.reconcile(sale(true), TaxRegime.REGIME_NORMAL, xml));
    }

    private static Sale sale(boolean withRtc) {
        Sale sale = new Sale();
        sale.setTotal(bd("90.00"));
        SaleItem item = new SaleItem();
        item.setItemNumber(1);
        item.setQuantity(bd("1"));
        item.setUnitPrice(bd("100.00"));
        item.setDiscount(bd("10.00"));
        item.setTotal(bd("90.00"));
        item.setIcmsCstCsosnSnapshot("00");
        item.setIcmsRate(bd("18"));
        item.setPisCstSnapshot("01");
        item.setPisRate(bd("1.65"));
        item.setCofinsCstSnapshot("01");
        item.setCofinsRate(bd("7.60"));
        if (withRtc) {
            item.setIbsCbsCalculation(new IbsCbsCalculation("000", "000001", bd("90.00"),
                new IbsCbsJurisdictionCalculation(bd("0.1"), bd("0.09")),
                new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO), bd("0.09"),
                new IbsCbsJurisdictionCalculation(bd("0.9"), bd("0.81"))));
        }
        sale.addItem(item);
        return sale;
    }

    private static String xml(boolean rtc) {
        String rtcItem = rtc
            ? "<IBSCBS><CST>000</CST><cClassTrib>000001</cClassTrib><gIBSCBS>"
                + "<vBC>90.00</vBC><gIBSUF><pIBSUF>0.1000</pIBSUF><vIBSUF>0.09</vIBSUF></gIBSUF>"
                + "<gIBSMun><pIBSMun>0.0000</pIBSMun><vIBSMun>0.00</vIBSMun></gIBSMun>"
                + "<vIBS>0.09</vIBS><gCBS><pCBS>0.9000</pCBS><vCBS>0.81</vCBS></gCBS>"
                + "</gIBSCBS></IBSCBS>"
            : "";
        String rtcTotals = rtc
            ? "<IBSCBSTot><vBCIBSCBS>90.00</vBCIBSCBS><gIBS>"
                + "<gIBSUF><vIBSUF>0.09</vIBSUF></gIBSUF>"
                + "<gIBSMun><vIBSMun>0.00</vIBSMun></gIBSMun><vIBS>0.09</vIBS>"
                + "</gIBS><gCBS><vCBS>0.81</vCBS></gCBS></IBSCBSTot>"
            : "";
        return "<enviNFe xmlns='http://www.portalfiscal.inf.br/nfe'><NFe><infNFe>"
            + "<det nItem='1'><prod><qCom>1.0000</qCom><vUnCom>100.0000000000</vUnCom>"
            + "<vProd>100.00</vProd><vDesc>10.00</vDesc></prod><imposto>"
            + "<ICMS><ICMS00><CST>00</CST><vBC>90.00</vBC><pICMS>18.0000</pICMS><vICMS>16.20</vICMS></ICMS00></ICMS>"
            + "<PIS><PISAliq><CST>01</CST><vBC>90.00</vBC><pPIS>1.6500</pPIS>"
            + "<vPIS>1.49</vPIS></PISAliq></PIS>"
            + "<COFINS><COFINSAliq><CST>01</CST><vBC>90.00</vBC>"
            + "<pCOFINS>7.6000</pCOFINS><vCOFINS>6.84</vCOFINS></COFINSAliq></COFINS>"
            + rtcItem + "</imposto></det><total><ICMSTot><vBC>90.00</vBC><vICMS>16.20</vICMS>"
            + "<vProd>100.00</vProd><vDesc>10.00</vDesc><vPIS>1.49</vPIS>"
            + "<vCOFINS>6.84</vCOFINS><vNF>90.00</vNF></ICMSTot>" + rtcTotals
            + "</total></infNFe></NFe></enviNFe>";
    }
}
