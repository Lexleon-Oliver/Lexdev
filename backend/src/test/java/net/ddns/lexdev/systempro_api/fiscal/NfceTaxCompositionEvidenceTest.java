package net.ddns.lexdev.systempro_api.fiscal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsCalculation;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsJurisdictionCalculation;

class NfceTaxCompositionEvidenceTest {
    private static BigDecimal bd(String value) { return new BigDecimal(value); }

    private static SaleItem item(String unitPrice, String quantity, String net, String discount) {
        SaleItem i = new SaleItem();
        i.setItemNumber(1);
        i.setQuantity(bd(quantity));
        i.setUnitPrice(bd(unitPrice));
        i.setTotal(bd(net));
        i.setDiscount(bd(discount));
        i.setIcmsCstCsosnSnapshot("00");
        i.setIcmsRate(bd("18"));
        i.setPisCstSnapshot("01");
        i.setPisRate(bd("1.65"));
        i.setCofinsCstSnapshot("01");
        i.setCofinsRate(bd("7.60"));
        return i;
    }

    private static IbsCbsCalculation rtc(String base, String ibsRate, String ibsAmount,
                                         String cbsRate, String cbsAmount) {
        return new IbsCbsCalculation("000", "000001", bd(base),
            new IbsCbsJurisdictionCalculation(bd(ibsRate), bd(ibsAmount)),
            new IbsCbsJurisdictionCalculation(bd("0"), bd("0")),
            bd(ibsAmount),
            new IbsCbsJurisdictionCalculation(bd(cbsRate), bd(cbsAmount)));
    }

    @Test
    void reconciliaValoresDoXmlMasNaoCriaValorLiquido() {
        SaleItem item = item("100.00", "1", "100.00", "0");
        item.setIbsCbsCalculation(rtc("100.00", "0.1", "0.10", "0.9", "0.90"));
        var evidence = NfceTaxCompositionEvidence.capture(item, TaxRegime.REGIME_NORMAL);
        assertEquals(bd("100.00"), evidence.grossProduct());
        assertEquals(bd("18.00"), evidence.icmsXml());
        assertEquals(bd("1.65"), evidence.pisXml());
        assertEquals(bd("7.60"), evidence.cofinsXml());
        assertEquals(bd("0.10"), evidence.ibsUfXml());
        assertEquals(bd("0.90"), evidence.cbsXml());
        assertTrue(evidence.rtcPresent());
        assertFalse(evidence.allReportedAmountsZero());
        assertFalse(item.hasAnyNfceLiquidSnapshotData());
    }

    @Test
    void separaValorBrutoDescontoEBaseTributavel() {
        SaleItem item = item("100.00", "1", "90.00", "10.00");
        item.setIbsCbsCalculation(rtc("90.00", "0.1", "0.09", "0.9", "0.81"));
        var evidence = NfceTaxCompositionEvidence.capture(item, TaxRegime.REGIME_NORMAL);
        assertEquals(bd("100.00"), evidence.grossProduct());
        assertEquals(bd("10.00"), evidence.commercialDiscount());
        assertEquals(bd("90.00"), evidence.netCommercial());
        assertEquals(bd("16.20"), evidence.icmsXml());
        assertEquals(bd("1.49"), evidence.pisXml());
        assertEquals(bd("6.84"), evidence.cofinsXml());
        assertFalse(item.hasAnyNfceLiquidSnapshotData());
    }

    @Test
    void distingueRtcNaoPersistidoDeTributacaoZero() {
        SaleItem item = item("10.00", "1", "10.00", "0");
        var evidence = NfceTaxCompositionEvidence.capture(item, TaxRegime.REGIME_NORMAL);
        assertFalse(evidence.rtcPresent());
        assertNull(evidence.cbsXml());
        assertFalse(evidence.allReportedAmountsZero());
    }

    @Test
    void rejeitaSnapshotRtcParcialSemNullPointer() {
        SaleItem item = item("10.00", "1", "10.00", "0");
        item.setIbsCbsCalculation(rtc("10.00", "0.1", "0.01", "0.9", "0.09"));
        item.setIbsCbsCalculation(null);
        // Para reproduzir uma coluna parcial de banco, reflexão é utilizada
        // apenas aqui (as entidades proíbem publicar RTC incompleto).
        try {
            var f = SaleItem.class.getDeclaredField("ibsCbsCstSnapshot");
            f.setAccessible(true);
            f.set(item, "000");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        assertThrows(FiscalIntegrationException.class,
            () -> NfceTaxCompositionEvidence.capture(item, TaxRegime.REGIME_NORMAL));
    }

    @Test
    void rejeitaSnapshotComApenasBaseMesmoSemCst() {
        SaleItem item = item("10.00", "1", "10.00", "0");
        try {
            var f = SaleItem.class.getDeclaredField("ibsCbsTaxBase");
            f.setAccessible(true);
            f.set(item, bd("10.00"));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        assertThrows(FiscalIntegrationException.class,
            () -> NfceTaxCompositionEvidence.capture(item, TaxRegime.REGIME_NORMAL));
    }

    @Test
    void rejeitaValorRtcDiferenteDaBaseEAliquota() {
        SaleItem item = item("100.00", "1", "100.00", "0");
        item.setIbsCbsCalculation(rtc("100.00", "0.1", "0.20", "0.9", "0.90"));
        assertThrows(FiscalIntegrationException.class,
            () -> NfceTaxCompositionEvidence.capture(item, TaxRegime.REGIME_NORMAL));
    }

    @Test
    void rejeitaPrecoNaoConciliadoEFracaoIndevida() {
        SaleItem item = item("10.00", "2", "25.00", "0");
        assertThrows(FiscalIntegrationException.class,
            () -> NfceTaxCompositionEvidence.capture(item, TaxRegime.REGIME_NORMAL));
        item.setTotal(bd("20.001"));
        assertThrows(FiscalIntegrationException.class,
            () -> NfceTaxCompositionEvidence.capture(item, TaxRegime.REGIME_NORMAL));
    }

    @Test
    void capturaQuantidadeFracionariaComArredondamentoFiscal() {
        SaleItem item = item("10.60", "0.125", "1.33", "0");
        item.setIcmsCstCsosnSnapshot("41");
        item.setPisCstSnapshot("06");
        item.setCofinsCstSnapshot("06");
        item.setIbsCbsCalculation(rtc("1.33", "0", "0", "0", "0"));
        var evidence = NfceTaxCompositionEvidence.capture(item, TaxRegime.REGIME_NORMAL);
        assertTrue(evidence.allReportedAmountsZero());
        assertEquals(bd("1.33"), evidence.netCommercial());
    }

    @Test
    void zeroMontanteNaoComprovaAliquotaZeroNemPrecoSemTributos() {
        SaleItem item = item("0.01", "1", "0.01", "0");
        item.setIcmsCstCsosnSnapshot("41");
        item.setPisCstSnapshot("06");
        item.setCofinsCstSnapshot("06");
        item.setIbsCbsCalculation(rtc("0.01", "0.1", "0.00", "0.9", "0.00"));
        var evidence = NfceTaxCompositionEvidence.capture(item, TaxRegime.REGIME_NORMAL);
        assertTrue(evidence.allReportedAmountsZero());
        assertFalse(item.hasAnyNfceLiquidSnapshotData());
    }

    @Test
    void csosnPermitidoNaoSeConfundeComImpostoDeclaradoZero() {
        SaleItem item = item("10.00", "1", "10.00", "0");
        item.setIcmsCstCsosnSnapshot("102");
        item.setPisCstSnapshot("06");
        item.setCofinsCstSnapshot("06");
        var evidence = NfceTaxCompositionEvidence.capture(item, TaxRegime.SIMPLES_NACIONAL);
        assertEquals(bd("0.00"), evidence.icmsXml());
        assertFalse(evidence.rtcPresent());
        assertFalse(evidence.allReportedAmountsZero());
    }

    @Test
    void bloqueiaCstNaoSuportadoSemCalcularImpostoZero() {
        SaleItem item = item("10.00", "1", "10.00", "0");
        item.setPisCstSnapshot("99");
        assertThrows(FiscalIntegrationException.class,
            () -> NfceTaxCompositionEvidence.capture(item, TaxRegime.REGIME_NORMAL));
        item.setPisCstSnapshot(null);
        assertThrows(FiscalIntegrationException.class,
            () -> NfceTaxCompositionEvidence.capture(item, TaxRegime.REGIME_NORMAL));
    }

    @Test
    void rejeitaClassificacaoRtcComLetrasMesmoQuePrefixoConfira() {
        SaleItem item = item("100.00", "1", "100.00", "0");
        item.setIbsCbsCalculation(rtc("100.00", "0.1", "0.10", "0.9", "0.90"));
        item.setIbsCbsCalculation(new IbsCbsCalculation("ABC", "ABC001", bd("100.00"),
            new IbsCbsJurisdictionCalculation(bd("0.1"), bd("0.10")),
            new IbsCbsJurisdictionCalculation(bd("0"), bd("0")), bd("0.10"),
            new IbsCbsJurisdictionCalculation(bd("0.9"), bd("0.90"))));
        assertThrows(FiscalIntegrationException.class,
            () -> NfceTaxCompositionEvidence.capture(item, TaxRegime.REGIME_NORMAL));
    }

    @Test
    void rejeitaInconsistenciaNoTotalDoIbs() {
        SaleItem item = item("100.00", "1", "100.00", "0");
        item.setIbsCbsCalculation(new IbsCbsCalculation("000", "000001", bd("100.00"),
            new IbsCbsJurisdictionCalculation(bd("0.1"), bd("0.10")),
            new IbsCbsJurisdictionCalculation(bd("0"), bd("0")),
            bd("0.11"),
            new IbsCbsJurisdictionCalculation(bd("0.9"), bd("0.90"))));
        assertThrows(FiscalIntegrationException.class,
            () -> NfceTaxCompositionEvidence.capture(item, TaxRegime.REGIME_NORMAL));
    }
}
