package net.ddns.lexdev.systempro_api.fiscal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;

class NfceLiquidProductValuesTest {

    @Test
    void deveValidarQuantidadeFracionariaSemArredondamentoSilencioso() {
        NfceLiquidProductValues values = new NfceLiquidProductValues(
            new BigDecimal("8.00"), new BigDecimal("1.00")
        );
        assertDoesNotThrow(() -> values.validateQuantity(new BigDecimal("0.125")));
    }

    @Test
    void deveAceitarToleranciaExataDeUmCentavoDaNotaTecnica() {
        NfceLiquidProductValues values = new NfceLiquidProductValues(
            new BigDecimal("8.00"), new BigDecimal("1.01")
        );
        assertDoesNotThrow(() -> values.validateQuantity(new BigDecimal("0.125")));
    }

    @Test
    void deveRejeitarDiferencaMaiorQueUmCentavo() {
        NfceLiquidProductValues values = new NfceLiquidProductValues(
            new BigDecimal("8.00"), new BigDecimal("1.02")
        );
        assertThrows(FiscalIntegrationException.class,
            () -> values.validateQuantity(new BigDecimal("0.125")));
    }

    @Test
    void deveRejeitarPrecisaoMonetariaInvalidaSemArredondar() {
        NfceLiquidProductValues values = new NfceLiquidProductValues(
            new BigDecimal("8.00"), new BigDecimal("1.001")
        );
        assertThrows(FiscalIntegrationException.class,
            () -> values.validateQuantity(new BigDecimal("0.125")));
    }

    @Test
    void deveRejeitarPrecisaoUnitariaMaiorQueDezCasas() {
        NfceLiquidProductValues values = new NfceLiquidProductValues(
            new BigDecimal("8.00000000001"), new BigDecimal("1.00")
        );
        assertThrows(FiscalIntegrationException.class,
            () -> values.validateQuantity(new BigDecimal("0.125")));
    }

    @Test
    void deveRejeitarValorNegativoOuAusente() {
        assertThrows(FiscalIntegrationException.class, () ->
            new NfceLiquidProductValues(new BigDecimal("-1"), BigDecimal.ZERO)
                .validateQuantity(BigDecimal.ONE));
        assertThrows(FiscalIntegrationException.class, () ->
            new NfceLiquidProductValues(null, BigDecimal.ZERO)
                .validateQuantity(BigDecimal.ONE));
    }

    @Test
    void deveRejeitarQuantidadesForaDaPrecisaoDoSchema() {
        NfceLiquidProductValues values = new NfceLiquidProductValues(
            new BigDecimal("8.00"), new BigDecimal("1.00")
        );
        assertThrows(FiscalIntegrationException.class,
            () -> values.validateQuantity(new BigDecimal("0.12501")));
        assertThrows(FiscalIntegrationException.class,
            () -> values.validateQuantity(BigDecimal.ZERO));
    }

    @Test
    void deveRejeitarExcessoDeDigitosInteiros() {
        NfceLiquidProductValues values = new NfceLiquidProductValues(
            new BigDecimal("100000000000"), new BigDecimal("1.00")
        );
        assertThrows(FiscalIntegrationException.class,
            () -> values.validateQuantity(new BigDecimal("0.125")));
    }
}
