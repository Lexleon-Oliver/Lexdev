package net.ddns.lexdev.systempro_api.fiscal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;
import net.ddns.lexdev.systempro_api.fiscal.NfcePriceFormationProvenance.Declaration;
import net.ddns.lexdev.systempro_api.fiscal.NfcePriceFormationProvenance.EmbeddedTax;
import net.ddns.lexdev.systempro_api.fiscal.NfcePriceFormationProvenance.ItemDeclaration;
import net.ddns.lexdev.systempro_api.fiscal.NfcePriceFormationProvenance.Source;
import net.ddns.lexdev.systempro_api.fiscal.NfcePriceFormationProvenance.TaxPart;

class NfcePriceFormationProvenanceTest {
    private static BigDecimal bd(String n) { return new BigDecimal(n); }
    private static final String ARBITRARY_SOURCE_DIGEST = "a".repeat(64);

    @Test
    void semDocumentoDePrecoRetornaBloqueioExplicito() {
        var assessment = NfcePriceFormationProvenance.assess(report(), null);
        assertEquals(NfcePriceFormationProvenance.Status.SOURCE_MISSING, assessment.status());
        assertEquals(64, assessment.reconciliationFingerprint().length());
        assertTrue(assessment.items().isEmpty());
        assertFalse(assessment.authorizesLiquidValues());
    }

    @Test
    void declaracaoPodeFecharAritmeticamenteMasNaoAutorizaValorLiquido() {
        var assessment = NfcePriceFormationProvenance.assess(report(), declaration(parts(), "65.47"));
        assertEquals(NfcePriceFormationProvenance.Status.ARITHMETICALLY_RECONCILED_NOT_VERIFIED,
            assessment.status());
        assertFalse(assessment.authorizesLiquidValues());
        assertEquals(1, assessment.items().size());
        assertEquals(bd("90.00"), assessment.items().get(0).commercialAfterDiscount());
        assertEquals(bd("24.53"), assessment.items().get(0).declaredTaxParts());
        assertEquals(bd("65.47"), assessment.items().get(0).declaredBase());
        assertTrue(assessment.items().get(0).comparisons().stream().allMatch(c -> c.equalAmounts()));
        assertFalse(assessment.blockers().isEmpty());
    }

    @Test
    void aliquotaOuImpostoDoXmlNaoComprovamValorEmbutido() {
        // Declara ICMS de R$ 15,00, embora o XML tenha R$ 16,20.
        // É uma divergência a ser investigada, não motivo para inventar dedução.
        List<TaxPart> changed = parts();
        changed.set(0, new TaxPart(EmbeddedTax.ICMS, bd("15.00")));
        var result = NfcePriceFormationProvenance.assess(report(), declaration(changed, "66.67"));
        assertFalse(result.items().get(0).comparisons().get(0).equalAmounts());
        assertFalse(result.authorizesLiquidValues());
    }

    @Test
    void naoInterpretaZerosDeclaradosComoTributoNaoEmbutidoComprovado() {
        List<TaxPart> zeros = new ArrayList<>();
        for (EmbeddedTax type : EmbeddedTax.values()) zeros.add(new TaxPart(type, bd("0.00")));
        var result = NfcePriceFormationProvenance.assess(report(), declaration(zeros, "90.00"));
        assertEquals(NfcePriceFormationProvenance.Status.ARITHMETICALLY_RECONCILED_NOT_VERIFIED,
            result.status());
        assertFalse(result.authorizesLiquidValues());
        assertFalse(result.items().get(0).comparisons().get(0).equalAmounts());
    }

    @Test
    void rejeitaDeclaraçãoDeOutroSnapshotMesmoComReferênciaAparentementeVálida() {
        var source = source();
        var different = new Declaration(source, "b".repeat(64),
            List.of(new ItemDeclaration(1, bd("65.47"), parts())));
        assertThrows(FiscalIntegrationException.class,
            () -> NfcePriceFormationProvenance.assess(report(), different));
    }

    @Test
    void rejeitaDeclaracaoCujaSomaDivergeEmUmCentavo() {
        assertThrows(FiscalIntegrationException.class,
            () -> NfcePriceFormationProvenance.assess(report(), declaration(parts(), "65.48")));
    }

    @Test
    void rejeitaItensAusentesOuRepetidos() {
        String fp = NfcePriceFormationProvenance.reconciliationFingerprint(report());
        assertThrows(FiscalIntegrationException.class, () -> NfcePriceFormationProvenance.assess(
            report(), new Declaration(source(), fp, List.of(
                new ItemDeclaration(2, bd("65.47"), parts())))));
        assertThrows(FiscalIntegrationException.class, () -> NfcePriceFormationProvenance.assess(
            report(), new Declaration(source(), fp, List.of(
                new ItemDeclaration(1, bd("65.47"), parts()),
                new ItemDeclaration(1, bd("65.47"), parts())))));
    }

    @Test
    void rejeitaParcelaDuplicadaOuOmitidaMesmoQuandoZero() {
        var parts = parts();
        parts.add(new TaxPart(EmbeddedTax.PIS, bd("0.00")));
        assertThrows(FiscalIntegrationException.class,
            () -> new ItemDeclaration(1, bd("65.47"), parts));
        parts.remove(parts.size() - 1);
        parts.remove(3);
        assertThrows(FiscalIntegrationException.class,
            () -> new ItemDeclaration(1, bd("65.47"), parts));
    }

    @Test
    void rejeitaValoresNegativosOuFracõesDeCentavo() {
        assertThrows(FiscalIntegrationException.class,
            () -> new TaxPart(EmbeddedTax.ICMS, bd("-0.01")));
        assertThrows(FiscalIntegrationException.class,
            () -> new TaxPart(EmbeddedTax.ICMS, bd("0.001")));
        assertThrows(FiscalIntegrationException.class,
            () -> new ItemDeclaration(1, bd("0.009"), parts()));
        assertThrows(FiscalIntegrationException.class,
            () -> new ItemDeclaration(1, null, parts()));
    }

    @Test
    void rejeitaHashesAusentesOuForaDoFormato() {
        assertThrows(FiscalIntegrationException.class, () -> new Source(
            "SYSTEMPRO", "PRICE-2026-0001", "v1", "não é hash"));
        assertThrows(FiscalIntegrationException.class, () -> new Declaration(
            source(), "invalid", List.of(new ItemDeclaration(1, bd("65.47"), parts()))));
    }

    @Test
    void fingerprintIgnoraApenasEscalaDecimalSemPerderDivergenciaFiscal() {
        var base = report();
        var alt = new NfceTaxCompositionEvidence(bd("100.0"), bd("10.0"), bd("90.0"),
            bd("16.2"), bd("1.490"), bd("6.840"), bd("90.0"), bd("0.090"),
            bd("0.0"), bd("0.810"), true);
        var consistent = new NfceTaxXmlReconciliation.Report(
            List.of(new NfceTaxXmlReconciliation.Item(1, alt)),
            bd("100.0"), bd("10.0"), bd("90.0"), bd("16.2"), bd("1.49"), bd("6.84"),
            bd("0.09"), bd("0"), bd("0.81"), true,
            NfceTaxXmlReconciliation.PriceInclusionStatus.NOT_DEMONSTRATED);
        assertEquals(NfcePriceFormationProvenance.reconciliationFingerprint(base),
            NfcePriceFormationProvenance.reconciliationFingerprint(consistent));

        var changed = new NfceTaxCompositionEvidence(bd("100.00"), bd("10.00"), bd("90.00"),
            bd("16.21"), bd("1.49"), bd("6.84"), bd("90.00"), bd("0.09"),
            bd("0.00"), bd("0.81"), true);
        var changedReport = new NfceTaxXmlReconciliation.Report(
            List.of(new NfceTaxXmlReconciliation.Item(1, changed)),
            bd("100.00"), bd("10.00"), bd("90.00"), bd("16.21"), bd("1.49"), bd("6.84"),
            bd("0.09"), bd("0.00"), bd("0.81"), true,
            NfceTaxXmlReconciliation.PriceInclusionStatus.NOT_DEMONSTRATED);
        assertFalse(NfcePriceFormationProvenance.reconciliationFingerprint(base)
            .equals(NfcePriceFormationProvenance.reconciliationFingerprint(changedReport)));
    }

    @Test
    void registroDeFonteNaoAutenticaArquivoNemProduzSnapshot() {
        var result = NfcePriceFormationProvenance.assess(report(), declaration(parts(), "65.47"));
        assertEquals(NfcePriceFormationProvenance.Status.ARITHMETICALLY_RECONCILED_NOT_VERIFIED,
            result.status());
        assertTrue(result.blockers().stream().anyMatch(b -> b.contains("não foram autenticados")));
        assertFalse(result.authorizesLiquidValues());
    }

    @Test
    void listasDefensivasImpedemAlterarDeclaracaoDepoisDaValidacao() {
        var input = parts();
        var declaration = declaration(input, "65.47");
        input.clear();
        assertEquals(7, declaration.items().get(0).parts().size());
        assertThrows(UnsupportedOperationException.class,
            () -> declaration.items().get(0).parts().clear());
    }

    private static Source source() {
        return new Source("SYSTEMPRO", "PRICE-2026-0001", "v1", ARBITRARY_SOURCE_DIGEST);
    }

    private static List<TaxPart> parts() {
        List<TaxPart> parts = new ArrayList<>();
        parts.add(new TaxPart(EmbeddedTax.ICMS, bd("16.20")));
        parts.add(new TaxPart(EmbeddedTax.PIS, bd("1.49")));
        parts.add(new TaxPart(EmbeddedTax.COFINS, bd("6.84")));
        parts.add(new TaxPart(EmbeddedTax.IPI, bd("0.00")));
        parts.add(new TaxPart(EmbeddedTax.ICMS_ST, bd("0.00")));
        parts.add(new TaxPart(EmbeddedTax.ISS, bd("0.00")));
        parts.add(new TaxPart(EmbeddedTax.OTHER, bd("0.00")));
        return parts;
    }

    private static Declaration declaration(List<TaxPart> parts, String base) {
        return new Declaration(source(), NfcePriceFormationProvenance.reconciliationFingerprint(report()),
            List.of(new ItemDeclaration(1, bd(base), parts)));
    }

    private static NfceTaxXmlReconciliation.Report report() {
        var e = new NfceTaxCompositionEvidence(bd("100.00"), bd("10.00"), bd("90.00"),
            bd("16.20"), bd("1.49"), bd("6.84"), bd("90.00"), bd("0.09"),
            bd("0.00"), bd("0.81"), true);
        return new NfceTaxXmlReconciliation.Report(
            List.of(new NfceTaxXmlReconciliation.Item(1, e)), bd("100.00"), bd("10.00"),
            bd("90.00"), bd("16.20"), bd("1.49"), bd("6.84"), bd("0.09"),
            bd("0.00"), bd("0.81"), true,
            NfceTaxXmlReconciliation.PriceInclusionStatus.NOT_DEMONSTRATED);
    }
}
