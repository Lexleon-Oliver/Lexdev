package net.ddns.lexdev.systempro_api.fiscal.rtc;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Resultado fiscal imutável de um item. É este objeto, e não o perfil do
 * produto, que futuramente será serializado no grupo IBSCBS do XML.
 */
public record IbsCbsCalculation(
    String cst,
    String cClassTrib,
    BigDecimal taxBase,
    IbsCbsJurisdictionCalculation ibsUf,
    IbsCbsJurisdictionCalculation ibsMunicipal,
    BigDecimal totalIbs,
    IbsCbsJurisdictionCalculation cbs
) {
    public IbsCbsCalculation {
        Objects.requireNonNull(cst, "cst");
        Objects.requireNonNull(cClassTrib, "cClassTrib");
        Objects.requireNonNull(taxBase, "taxBase");
        Objects.requireNonNull(ibsUf, "ibsUf");
        Objects.requireNonNull(ibsMunicipal, "ibsMunicipal");
        Objects.requireNonNull(totalIbs, "totalIbs");
        Objects.requireNonNull(cbs, "cbs");
    }
}
