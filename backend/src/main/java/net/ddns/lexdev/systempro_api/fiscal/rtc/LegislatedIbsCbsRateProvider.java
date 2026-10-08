package net.ddns.lexdev.systempro_api.fiscal.rtc;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;

/**
 * Alíquotas padrão publicadas para os DF-e durante a transição da RTC.
 * Esta implementação é deliberadamente fechada em 2026: anos seguintes
 * só podem ser habilitados depois da publicação das respectivas alíquotas.
 */
@Component
public class LegislatedIbsCbsRateProvider implements IbsCbsRateProvider {
    private static final IbsCbsRates RATES_2026 = new IbsCbsRates(
        new BigDecimal("0.100000"),
        new BigDecimal("0.000000"),
        new BigDecimal("0.900000")
    );

    @Override
    public IbsCbsRates resolve(LocalDate operationDate) {
        if (operationDate == null) {
            throw new FiscalConfigurationException("Data da operação é obrigatória para resolver as alíquotas IBS/CBS.");
        }
        if (operationDate.getYear() != 2026) {
            throw new FiscalConfigurationException(
                "As alíquotas padrão IBS/CBS do ano " + operationDate.getYear()
                    + " ainda não estão parametrizadas no emissor."
            );
        }
        return RATES_2026;
    }
}