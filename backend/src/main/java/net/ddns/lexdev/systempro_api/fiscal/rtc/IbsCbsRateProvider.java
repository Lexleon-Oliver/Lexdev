package net.ddns.lexdev.systempro_api.fiscal.rtc;

import java.time.LocalDate;

/**
 * Fronteira para alíquotas vigentes. As alíquotas não pertencem ao cadastro do produto
 * nem à tabela cClassTrib e podem variar por vigência/contexto fiscal.
 */
public interface IbsCbsRateProvider {
    IbsCbsRates resolve(LocalDate operationDate);
}
