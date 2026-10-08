package net.ddns.lexdev.systempro_api.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsCalculation;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsJurisdictionCalculation;

class SaleItemIbsCbsSnapshotTest {

    @Test
    void devePreservarResultadoCalculadoComoSnapshotDoItem() {
        IbsCbsCalculation calculation = new IbsCbsCalculation(
            "000",
            "000001",
            new BigDecimal("100.00"),
            new IbsCbsJurisdictionCalculation(new BigDecimal("0.100000"), new BigDecimal("0.10")),
            new IbsCbsJurisdictionCalculation(new BigDecimal("0.000000"), new BigDecimal("0.00")),
            new BigDecimal("0.10"),
            new IbsCbsJurisdictionCalculation(new BigDecimal("0.900000"), new BigDecimal("0.90"))
        );

        SaleItem item = new SaleItem();
        item.setIbsCbsCalculation(calculation);

        assertEquals(calculation, item.getIbsCbsCalculation());
        assertEquals("000", item.getIbsCbsCstSnapshot());
        assertEquals("000001", item.getCClassTribSnapshot());
    }

    @Test
    void devePermitirAusenciaDeSnapshotEnquantoRtcNaoForAplicavel() {
        SaleItem item = new SaleItem();

        assertNull(item.getIbsCbsCalculation());

        item.setIbsCbsCalculation(null);
        assertNull(item.getIbsCbsCalculation());
    }
}
