package net.ddns.lexdev.systempro_api.fiscal.rtc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;

class IbsCbsCalculatorTest {

    @Test
    void calculaSeparadamenteIbsUfIbsMunicipioECbsSemPersistirResultadoNoPerfil() {
        IbsCbsTaxRule rule = regular(true, "0.1000", "0.0000", "0.9000");

        IbsCbsCalculation result = IbsCbsCalculator.calculateRegular(new BigDecimal("123.45"), rule);

        assertEquals(new BigDecimal("123.45"), result.taxBase());
        assertEquals(new BigDecimal("0.12"), result.ibsUf().amount());
        assertEquals(new BigDecimal("0.00"), result.ibsMunicipal().amount());
        assertEquals(new BigDecimal("0.12"), result.totalIbs());
        assertEquals(new BigDecimal("1.11"), result.cbs().amount());
        assertEquals("000", result.cst());
        assertEquals("000001", result.cClassTrib());
    }

    @Test
    void rejeitaClassificacaoQueNaoPermiteNfce() {
        IbsCbsTaxRule rule = regular(false, "0.1000", "0.0000", "0.9000");

        assertThrows(FiscalConfigurationException.class,
            () -> IbsCbsCalculator.calculateRegular(new BigDecimal("100.00"), rule));
    }

    @Test
    void naoTrataRegraEspecialComoTributacaoRegular() {
        IbsCbsTaxRule rule = new IbsCbsTaxRule(
            "620", "620001", IbsCbsTaxationMode.MONOPHASIC, true,
            null, null, null
        );

        assertThrows(FiscalConfigurationException.class,
            () -> IbsCbsCalculator.calculateRegular(new BigDecimal("100.00"), rule));
    }

    @Test
    void rejeitaBaseNegativa() {
        IbsCbsTaxRule rule = regular(true, "0.1000", "0.0000", "0.9000");

        assertThrows(FiscalConfigurationException.class,
            () -> IbsCbsCalculator.calculateRegular(new BigDecimal("-0.01"), rule));
    }

    @Test
    void rejeitaRegraRegularSemAliquotasResolvidas() {
        IbsCbsTaxRule rule = regular(true, null, "0.0000", "0.9000");

        assertThrows(FiscalConfigurationException.class,
            () -> IbsCbsCalculator.calculateRegular(new BigDecimal("100.00"), rule));
    }

    private static IbsCbsTaxRule regular(boolean nfceAllowed, String uf, String mun, String cbs) {
        return new IbsCbsTaxRule(
            "000",
            "000001",
            IbsCbsTaxationMode.REGULAR,
            nfceAllowed,
            decimal(uf),
            decimal(mun),
            decimal(cbs)
        );
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
