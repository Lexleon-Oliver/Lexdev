package net.ddns.lexdev.systempro_api.fiscal.rtc;

/**
 * Famílias de cálculo previstas pelo leiaute RTC. Nesta etapa somente REGULAR
 * possui cálculo implementado; as demais existem para impedir que uma regra
 * especial seja tratada silenciosamente como tributação regular.
 */
public enum IbsCbsTaxationMode {
    REGULAR,
    MONOPHASIC,
    TRANSFER_CREDIT,
    PRESUMED_CREDIT,
    GOVERNMENT_PURCHASE,
    OTHER_SPECIAL
}
