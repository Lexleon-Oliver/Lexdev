package net.ddns.lexdev.systempro_api.fiscal.rtc;

import java.math.BigDecimal;

/** Alíquotas vigentes resolvidas para a data/operação, separadas da classificação cClassTrib. */
public record IbsCbsRates(
    BigDecimal ibsUfRate,
    BigDecimal ibsMunicipalRate,
    BigDecimal cbsRate
) {
}
