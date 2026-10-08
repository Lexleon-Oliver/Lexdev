package net.ddns.lexdev.systempro_api.fiscal.validation;

import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;

public final class IbsCbsClassificationValidator {

    private IbsCbsClassificationValidator() {
    }

    public static void validate(String cst, String cClassTrib) {
        boolean hasCst = hasText(cst);
        boolean hasClass = hasText(cClassTrib);

        if (hasCst != hasClass) {
            throw new FiscalConfigurationException(
                "CST IBS/CBS e cClassTrib devem ser informados em conjunto."
            );
        }
        if (!hasCst) {
            return;
        }
        if (!cst.matches("\\d{3}")) {
            throw new FiscalConfigurationException("CST IBS/CBS deve possuir exatamente 3 dígitos.");
        }
        if (!cClassTrib.matches("\\d{6}")) {
            throw new FiscalConfigurationException("cClassTrib deve possuir exatamente 6 dígitos.");
        }
        if (!cClassTrib.startsWith(cst)) {
            throw new FiscalConfigurationException(
                "Os três primeiros dígitos do cClassTrib devem corresponder ao CST IBS/CBS."
            );
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
