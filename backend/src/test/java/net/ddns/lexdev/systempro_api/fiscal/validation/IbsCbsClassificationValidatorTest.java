package net.ddns.lexdev.systempro_api.fiscal.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;

class IbsCbsClassificationValidatorTest {

    @Test
    void acceptsClassificationWhenCstAndClassAreStructurallyConsistent() {
        assertDoesNotThrow(() -> IbsCbsClassificationValidator.validate("000", "000001"));
    }

    @Test
    void acceptsAbsenceBecauseRtcEmissionIsStillBlockedUntilFullImplementation() {
        assertDoesNotThrow(() -> IbsCbsClassificationValidator.validate(null, null));
        assertDoesNotThrow(() -> IbsCbsClassificationValidator.validate("", ""));
    }

    @Test
    void rejectsOnlyOneClassificationField() {
        FiscalConfigurationException ex = assertThrows(
            FiscalConfigurationException.class,
            () -> IbsCbsClassificationValidator.validate("000", null)
        );
        assertTrue(ex.getMessage().contains("informados em conjunto"));
    }

    @Test
    void rejectsInvalidLengthsAndNonDigits() {
        assertThrows(FiscalConfigurationException.class,
            () -> IbsCbsClassificationValidator.validate("00", "000001"));
        assertThrows(FiscalConfigurationException.class,
            () -> IbsCbsClassificationValidator.validate("000", "00001"));
        assertThrows(FiscalConfigurationException.class,
            () -> IbsCbsClassificationValidator.validate("00A", "00A001"));
    }

    @Test
    void rejectsClassWhosePrefixDoesNotMatchCst() {
        FiscalConfigurationException ex = assertThrows(
            FiscalConfigurationException.class,
            () -> IbsCbsClassificationValidator.validate("200", "210001")
        );
        assertTrue(ex.getMessage().contains("corresponder ao CST"));
    }
}
