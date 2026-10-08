package net.ddns.lexdev.systempro_api.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.FiscalProductProfile;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;
import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsCalculator;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsTaxRule;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsTaxRuleResolver;

/**
 * Fecha a classificação RTC e o resultado calculado no momento da venda.
 * O XML posterior deve consumir o snapshot do SaleItem, nunca o cadastro mutável.
 */
@Service
public class IbsCbsSaleSnapshotService {
    private static final int NFCE_MODEL = 65;

    private final IbsCbsTaxRuleResolver ruleResolver;

    public IbsCbsSaleSnapshotService(IbsCbsTaxRuleResolver ruleResolver) {
        this.ruleResolver = ruleResolver;
    }

    public void apply(
        FiscalEstablishment establishment,
        FiscalProductProfile profile,
        SaleItem item,
        LocalDate operationDate
    ) {
        if (establishment.getTaxRegime() != TaxRegime.REGIME_NORMAL) {
            item.setIbsCbsCalculation(null);
            return;
        }

        String cst = blankToNull(profile.getIbsCbsCst());
        String cClassTrib = blankToNull(profile.getCClassTrib());
        if (cst == null || cClassTrib == null) {
            throw new FiscalConfigurationException(
                "Produto em Regime Normal precisa de CST IBS/CBS e cClassTrib para emissão da NFC-e."
            );
        }

        IbsCbsTaxRule rule = ruleResolver.resolve(cst, cClassTrib, operationDate, NFCE_MODEL);
        item.setIbsCbsCalculation(IbsCbsCalculator.calculateRegular(item.getTotal(), rule));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}