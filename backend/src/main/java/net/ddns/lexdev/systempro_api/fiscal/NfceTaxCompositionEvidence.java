package net.ddns.lexdev.systempro_api.fiscal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsCalculation;

/**
 * Conciliação SOMENTE LEITURA dos valores calculáveis a partir do item vendido.
 * ICMS/PIS/COFINS seguem as regras atualmente serializadas pelo emissor NFC-e.
 * IBS/CBS vêm exclusivamente do snapshot persistido e são reconciliados.
 *
 * Os montantes apurados NÃO demonstram que cada tributo está incluído em vProd:
 * NÃO subtrair sua soma do preço para produzir vUnComLiq ou vProdLiq.
 */
public record NfceTaxCompositionEvidence(
    BigDecimal grossProduct,
    BigDecimal commercialDiscount,
    BigDecimal netCommercial,
    BigDecimal icmsXml,
    BigDecimal pisXml,
    BigDecimal cofinsXml,
    BigDecimal rtcTaxBase,
    BigDecimal ibsUfXml,
    BigDecimal ibsMunicipalXml,
    BigDecimal cbsXml,
    boolean rtcPresent
) {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final List<String> NO_PIS_COFINS = List.of("04", "05", "06", "07", "08", "09");

    public static NfceTaxCompositionEvidence capture(SaleItem item, TaxRegime regime) {
        if (item == null || regime == null) {
            throw invalid("item e regime são obrigatórios");
        }
        BigDecimal net = cents(item.getTotal(), "total comercial");
        BigDecimal discount = cents(item.getDiscount(), "desconto comercial");
        BigDecimal gross = net.add(discount);
        if (gross.signum() <= 0 || item.getQuantity() == null || item.getQuantity().signum() <= 0
            || item.getQuantity().stripTrailingZeros().scale() > 4
            || item.getUnitPrice() == null || item.getUnitPrice().signum() <= 0
            || item.getQuantity().multiply(item.getUnitPrice())
                .setScale(2, RoundingMode.HALF_UP).compareTo(gross) != 0) {
            throw invalid("preço, quantidade e total do produto não conciliam");
        }
        BigDecimal icms = icmsValue(item, regime, net);
        BigDecimal pis = pisCofinsValue(item.getPisCstSnapshot(), item.getPisRate(), net, "PIS");
        BigDecimal cofins = pisCofinsValue(item.getCofinsCstSnapshot(), item.getCofinsRate(), net, "COFINS");

        // Não reconstruir um RTC parcial: SaleItem.getIbsCbsCalculation() usa
        // records que rejeitam null no construtor. Uma lacuna é uma falha fiscal.
        IbsCbsCalculation rtc = null;
        BigDecimal rtcBase = null, uf = null, municipality = null, cbs = null;
        boolean anyRtcField = item.getIbsCbsCstSnapshot() != null
            || item.getCClassTribSnapshot() != null || item.getIbsCbsTaxBase() != null
            || item.getIbsUfRate() != null || item.getIbsUfAmount() != null
            || item.getIbsMunicipalRate() != null || item.getIbsMunicipalAmount() != null
            || item.getIbsTotalAmount() != null || item.getCbsRateRtc() != null
            || item.getCbsAmount() != null;
        if (anyRtcField) {
            if (item.getIbsCbsCstSnapshot() == null || item.getCClassTribSnapshot() == null
                || item.getIbsCbsTaxBase() == null || item.getIbsUfRate() == null
                || item.getIbsUfAmount() == null || item.getIbsMunicipalRate() == null
                || item.getIbsMunicipalAmount() == null || item.getIbsTotalAmount() == null
                || item.getCbsRateRtc() == null || item.getCbsAmount() == null) {
                throw invalid("snapshot IBS/CBS incompleto");
            }
            rtc = item.getIbsCbsCalculation();
            rtcBase = cents(rtc.taxBase(), "base RTC");
            uf = cents(rtc.ibsUf().amount(), "IBS UF");
            municipality = cents(rtc.ibsMunicipal().amount(), "IBS Município");
            cbs = cents(rtc.cbs().amount(), "CBS");
            if (!item.getIbsCbsCstSnapshot().matches("[0-9]{3}")
                || !item.getCClassTribSnapshot().matches("[0-9]{6}")
                || !item.getCClassTribSnapshot().startsWith(item.getIbsCbsCstSnapshot())
                || calculated(rtcBase, rtc.ibsUf().rate(), "IBS UF").compareTo(uf) != 0
                || calculated(rtcBase, rtc.ibsMunicipal().rate(), "IBS Município")
                    .compareTo(municipality) != 0
                || calculated(rtcBase, rtc.cbs().rate(), "CBS").compareTo(cbs) != 0
                || cents(rtc.totalIbs(), "IBS total").compareTo(uf.add(municipality)) != 0) {
                throw invalid("snapshot IBS/CBS não concilia com base, alíquotas ou total");
            }
        }
        return new NfceTaxCompositionEvidence(gross, discount, net, icms, pis, cofins,
            rtcBase, uf, municipality, cbs, rtc != null);
    }

    /**
     * Apenas um diagnóstico: zero apurado NÃO prova ausência de imposto
     * embutido no preço (nem autoriza produzir valores líquidos).
     */
    public boolean allReportedAmountsZero() {
        return rtcPresent && icmsXml.signum() == 0 && pisXml.signum() == 0
            && cofinsXml.signum() == 0 && ibsUfXml.signum() == 0
            && ibsMunicipalXml.signum() == 0 && cbsXml.signum() == 0;
    }

    private static BigDecimal icmsValue(SaleItem item, TaxRegime regime, BigDecimal net) {
        String code = item.getIcmsCstCsosnSnapshot();
        if (regime.usesCsosn()) {
            if (code != null && List.of("102", "103", "300", "400").contains(code)) return BigDecimal.ZERO.setScale(2);
            throw invalid("CSOSN não suportado pelo XML: " + code);
        }
        if ("00".equals(code)) return calculated(net, item.getIcmsRate(), "ICMS");
        if ("40".equals(code) || "41".equals(code)) return BigDecimal.ZERO.setScale(2);
        throw invalid("CST ICMS não suportado pelo XML: " + code);
    }

    private static BigDecimal pisCofinsValue(
        String cst, BigDecimal rate, BigDecimal base, String name
    ) {
        if (cst != null && NO_PIS_COFINS.contains(cst)) return BigDecimal.ZERO.setScale(2);
        if ("01".equals(cst) || "02".equals(cst)) return calculated(base, rate, name);
        throw invalid("CST " + name + " não suportado pelo XML: " + cst);
    }

    private static BigDecimal calculated(BigDecimal base, BigDecimal rate, String name) {
        if (rate == null || rate.signum() < 0) throw invalid("alíquota " + name + " ausente/negativa");
        return base.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal cents(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) throw invalid(name + " ausente/negativo");
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw invalid(name + " contém fração de centavo");
        }
    }

    private static FiscalIntegrationException invalid(String reason) {
        return new FiscalIntegrationException("Evidência tributária NFC-e inválida: " + reason + ".");
    }
}
