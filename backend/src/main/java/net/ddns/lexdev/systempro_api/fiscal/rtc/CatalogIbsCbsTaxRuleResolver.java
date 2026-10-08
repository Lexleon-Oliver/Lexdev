package net.ddns.lexdev.systempro_api.fiscal.rtc;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import net.ddns.lexdev.systempro_api.domain.IbsCbsTaxClassification;
import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;
import net.ddns.lexdev.systempro_api.repository.IbsCbsTaxClassificationRepository;

/** Resolve a regra usando o snapshot local da tabela oficial e um provedor separado de alíquotas. */
@Component
public class CatalogIbsCbsTaxRuleResolver implements IbsCbsTaxRuleResolver {
    private static final int NFCE_MODEL = 65;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final IbsCbsTaxClassificationRepository repository;
    private final IbsCbsRateProvider rateProvider;

    public CatalogIbsCbsTaxRuleResolver(IbsCbsTaxClassificationRepository repository, IbsCbsRateProvider rateProvider) {
        this.repository = repository;
        this.rateProvider = rateProvider;
    }

    @Override
    public IbsCbsTaxRule resolve(String cst, String cClassTrib, LocalDate operationDate, int fiscalDocumentModel) {
        if (operationDate == null) {
            throw new FiscalConfigurationException("Data da operação é obrigatória para resolver IBS/CBS.");
        }

        IbsCbsTaxClassification classification = repository
            .findApplicable(cst, cClassTrib, operationDate)
            .orElseThrow(() -> new FiscalConfigurationException(
                "CST IBS/CBS e cClassTrib não constam no catálogo fiscal vigente para a data da operação."
            ));

        boolean allowed = fiscalDocumentModel != NFCE_MODEL || classification.isNfceAllowed();
        IbsCbsRates rates = rateProvider.resolve(operationDate);
        if (rates == null) {
            throw new FiscalConfigurationException("Alíquotas IBS/CBS vigentes não foram resolvidas.");
        }

        return new IbsCbsTaxRule(
            classification.getCst(),
            classification.getCClassTrib(),
            classification.getTaxationMode(),
            allowed,
            reduced(rates.ibsUfRate(), classification.getIbsReductionPercent()),
            reduced(rates.ibsMunicipalRate(), classification.getIbsReductionPercent()),
            reduced(rates.cbsRate(), classification.getCbsReductionPercent())
        );
    }

    private static BigDecimal reduced(BigDecimal rate, BigDecimal reductionPercent) {
        if (rate == null || reductionPercent == null || reductionPercent.signum() == 0) {
            return rate;
        }
        if (reductionPercent.signum() < 0 || reductionPercent.compareTo(ONE_HUNDRED) > 0) {
            throw new FiscalConfigurationException("Percentual de redução IBS/CBS do catálogo está fora do intervalo de 0 a 100.");
        }
        return rate.multiply(ONE_HUNDRED.subtract(reductionPercent)).divide(ONE_HUNDRED);
    }
}