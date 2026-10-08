package net.ddns.lexdev.systempro_api.fiscal.rtc;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Regra tributária já resolvida a partir da classificação vigente.
 * Não é entidade de produto e não armazena valores calculados da operação.
 */
public record IbsCbsTaxRule(
    String cst,
    String cClassTrib,
    IbsCbsTaxationMode taxationMode,
    boolean nfceAllowed,
    BigDecimal ibsUfRate,
    BigDecimal ibsMunicipalRate,
    BigDecimal cbsRate
) {
    public IbsCbsTaxRule {
        Objects.requireNonNull(cst, "cst");
        Objects.requireNonNull(cClassTrib, "cClassTrib");
        Objects.requireNonNull(taxationMode, "taxationMode");
        validateRate(ibsUfRate, "ibsUfRate");
        validateRate(ibsMunicipalRate, "ibsMunicipalRate");
        validateRate(cbsRate, "cbsRate");
    }

    private static void validateRate(BigDecimal rate, String field) {
        if (rate != null && rate.signum() < 0) {
            throw new IllegalArgumentException(field + " não pode ser negativo.");
        }
    }
}
