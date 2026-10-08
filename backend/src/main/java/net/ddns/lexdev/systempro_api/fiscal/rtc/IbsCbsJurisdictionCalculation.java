package net.ddns.lexdev.systempro_api.fiscal.rtc;

import java.math.BigDecimal;
import java.util.Objects;

/** Alíquota e valor calculado para uma competência do IBS. */
public record IbsCbsJurisdictionCalculation(BigDecimal rate, BigDecimal amount) {
    public IbsCbsJurisdictionCalculation {
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(amount, "amount");
    }
}
