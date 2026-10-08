package net.ddns.lexdev.systempro_api.fiscal.rtc;

import java.math.BigDecimal;
import java.math.RoundingMode;

import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;

/**
 * Núcleo de cálculo da tributação regular IBS/CBS.
 * Não consulta tabelas, não conhece JPA e não monta XML.
 */
public final class IbsCbsCalculator {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int MONEY_SCALE = 2;

    private IbsCbsCalculator() {
    }

    public static IbsCbsCalculation calculateRegular(BigDecimal taxBase, IbsCbsTaxRule rule) {
        if (taxBase == null || taxBase.signum() < 0) {
            throw new FiscalConfigurationException("Base de cálculo IBS/CBS deve ser informada e não pode ser negativa.");
        }
        if (rule == null) {
            throw new FiscalConfigurationException("Regra IBS/CBS não foi resolvida.");
        }
        if (!rule.nfceAllowed()) {
            throw new FiscalConfigurationException("A classificação IBS/CBS informada não permite emissão de NFC-e.");
        }
        if (rule.taxationMode() != IbsCbsTaxationMode.REGULAR) {
            throw new FiscalConfigurationException(
                "A classificação IBS/CBS exige um grupo de tributação ainda não suportado pelo cálculo regular."
            );
        }
        requireRate(rule.ibsUfRate(), "alíquota IBS UF");
        requireRate(rule.ibsMunicipalRate(), "alíquota IBS Município");
        requireRate(rule.cbsRate(), "alíquota CBS");

        BigDecimal base = taxBase.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal ibsUf = amount(base, rule.ibsUfRate());
        BigDecimal ibsMun = amount(base, rule.ibsMunicipalRate());
        BigDecimal cbs = amount(base, rule.cbsRate());

        return new IbsCbsCalculation(
            rule.cst(),
            rule.cClassTrib(),
            base,
            new IbsCbsJurisdictionCalculation(rule.ibsUfRate(), ibsUf),
            new IbsCbsJurisdictionCalculation(rule.ibsMunicipalRate(), ibsMun),
            ibsUf.add(ibsMun).setScale(MONEY_SCALE, RoundingMode.HALF_UP),
            new IbsCbsJurisdictionCalculation(rule.cbsRate(), cbs)
        );
    }

    private static BigDecimal amount(BigDecimal base, BigDecimal rate) {
        return base.multiply(rate).divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static void requireRate(BigDecimal rate, String field) {
        if (rate == null || rate.signum() < 0) {
            throw new FiscalConfigurationException(field + " deve ser informada e não pode ser negativa.");
        }
    }
}
