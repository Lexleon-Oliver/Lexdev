package net.ddns.lexdev.systempro_api.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TaxRegimeTest {

    @Test
    void mapsSupportedRegimesExplicitlyToSefazCrt() {
        assertEquals("1", TaxRegime.SIMPLES_NACIONAL.sefazCrtCode());
        assertTrue(TaxRegime.SIMPLES_NACIONAL.usesCsosn());

        assertEquals("3", TaxRegime.REGIME_NORMAL.sefazCrtCode());
        assertFalse(TaxRegime.REGIME_NORMAL.usesCsosn());
    }
}
