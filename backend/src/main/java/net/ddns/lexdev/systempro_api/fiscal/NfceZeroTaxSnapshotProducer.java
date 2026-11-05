package net.ddns.lexdev.systempro_api.fiscal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsCalculation;

/**
 * Produtor fiscal deliberadamente limitado a vendas SEM TRIBUTAÇÃO do item.
 * Não estima preço líquido de operações tributadas, não abate IBS/CBS do vProd
 * e não trata desconto, ICMS-ST, IPI, frete ou regimes especiais.
 *
 * A opção de produção de snapshots nasce DESATIVADA. Para uma venda inteira,
 * primeiro valida/calcula todos os itens e só depois aplica os snapshots:
 * jamais persiste mistura de itens com e sem dados líquidos.
 *
 * Este contrato não substitui um motor fiscal que identifique exaustivamente
 * os tributos incorporados ao preço para os demais regimes e operações.
 */
public final class NfceZeroTaxSnapshotProducer {
    public static final String CALCULATION_SOURCE = "NT2026.008:ZERO-TAX-200003-V2";
    private static final ZoneId FISCAL_ZONE = ZoneId.of("America/Sao_Paulo");

    private NfceZeroTaxSnapshotProducer() { }

    public static void apply(FiscalEstablishment establishment, Sale sale, LocalDate operationDate) {
        if (establishment == null || establishment.getTaxRegime() != TaxRegime.REGIME_NORMAL) {
            throw unsupported("somente Regime Normal possui apuração RTC persistida nesta etapa");
        }
        if (operationDate == null || operationDate.getYear() != 2026) {
            throw unsupported("motor de valor líquido zero-tributo limitado a operações de 2026");
        }
        if (sale == null || sale.getItems() == null || sale.getItems().isEmpty()) {
            throw unsupported("venda sem itens fiscais");
        }
        if (sale.getSaleAt() == null || !operationDate.equals(sale.getSaleAt().atZone(FISCAL_ZONE).toLocalDate())) {
            throw unsupported("data fiscal da venda divergente da operação");
        }
        if (!zero(sale.getDiscount())) {
            throw unsupported("desconto global precisa ser zero");
        }

        Map<SaleItem, NfceLiquidProductValues> candidate = new LinkedHashMap<>();
        Set<Integer> itemNumbers = new HashSet<>();
        for (SaleItem item : sale.getItems()) {
            if (item == null || candidate.containsKey(item)) {
                throw unsupported("item ausente ou duplicado");
            }
            if (item.getItemNumber() <= 0 || !itemNumbers.add(item.getItemNumber())) {
                throw unsupported("numeração fiscal de item inválida ou duplicada");
            }
            candidate.put(item, calculate(item));
        }
        // Somente depois de todos os itens passarem, modificar a venda.
        candidate.forEach((item, values) -> item.setNfceLiquidSnapshot(values, CALCULATION_SOURCE));
    }

    private static NfceLiquidProductValues calculate(SaleItem item) {
        if (item.hasAnyNfceLiquidSnapshotData()) {
            throw unsupported("snapshot pré-existente não pode ser sobrescrito");
        }
        if (!"5102".equals(item.getCfopSnapshot()) || !"0".equals(item.getOriginSnapshot())) {
            throw unsupported("somente CFOP 5102 e origem nacional 0 são cobertos");
        }
        if (!("40".equals(item.getIcmsCstCsosnSnapshot())
                || "41".equals(item.getIcmsCstCsosnSnapshot()))
            || !"06".equals(item.getPisCstSnapshot())
            || !"06".equals(item.getCofinsCstSnapshot())) {
            throw unsupported("CST ICMS/PIS/COFINS não comprovam operação zero-tributo");
        }
        if (!zeroOrAbsent(item.getIcmsRate()) || !zeroOrAbsent(item.getPisRate())
            || !zeroOrAbsent(item.getCofinsRate())) {
            throw unsupported("alíquota tradicional conflitante com CST zero-tributo");
        }
        if (!zero(item.getDiscount())) {
            throw unsupported("itens com desconto ainda não têm regra líquida validada");
        }
        // Exemplo estritamente verificável do Anexo I da LC 214/2025:
        // arroz nas subposições 1006.20, 1006.30 e NCM 1006.40.00.
        // Outras famílias do cClassTrib 200003 dependem de condições legais
        // adicionais e de uma política de classificação ainda não implementada.
        if (!eligibleRiceNcm(item.getNcmSnapshot())) {
            throw unsupported("NCM fora do recorte arroz/Anexo I do CST 200 cClassTrib 200003");
        }
        // Verificar os campos persistidos ANTES de reconstruir o record RTC:
        // a reconstrução falharia com NullPointerException em snapshots parciais.
        // CST 000 / cClassTrib 000001 significa tributação integral. A
        // única hipótese de alíquota zero coberta por esta versão é a cesta
        // básica nacional (CST 200 / cClassTrib 200003), redução de 100%.
        // O enquadramento NCM/Anexo I deve ser confirmado no perfil fiscal.
        if (!"200".equals(item.getIbsCbsCstSnapshot())
            || !"200003".equals(item.getCClassTribSnapshot())
            || item.getIbsCbsTaxBase() == null
            || item.getIbsUfRate() == null || item.getIbsUfAmount() == null
            || item.getIbsMunicipalRate() == null || item.getIbsMunicipalAmount() == null
            || item.getIbsTotalAmount() == null
            || item.getCbsRateRtc() == null || item.getCbsAmount() == null) {
            throw unsupported("campos RTC incompletos ou classificação não coberta");
        }
        IbsCbsCalculation rtc = item.getIbsCbsCalculation();
        if (rtc == null || !"200".equals(rtc.cst()) || !"200003".equals(rtc.cClassTrib())
            || rtc.taxBase() == null || rtc.ibsUf() == null || rtc.ibsMunicipal() == null
            || rtc.cbs() == null || !zero(rtc.ibsUf().rate()) || !zero(rtc.ibsUf().amount())
            || !zero(rtc.ibsMunicipal().rate()) || !zero(rtc.ibsMunicipal().amount())
            || !zero(rtc.totalIbs()) || !zero(rtc.cbs().rate()) || !zero(rtc.cbs().amount())) {
            throw unsupported("snapshot RTC não comprova redução integral de IBS e CBS");
        }
        BigDecimal total = exactCents(item.getTotal());
        if (total.signum() <= 0 || rtc.taxBase().compareTo(total) != 0) {
            throw unsupported("total ou base RTC incompatível com valor do item");
        }
        if (item.getUnitPrice() == null || item.getUnitPrice().signum() <= 0
            || item.getQuantity() == null || item.getQuantity().signum() <= 0) {
            throw unsupported("quantidade ou preço unitário inválido");
        }
        BigDecimal gross = item.getQuantity().multiply(item.getUnitPrice())
            .setScale(2, RoundingMode.HALF_UP);
        if (gross.compareTo(total) != 0) {
            throw unsupported("total não corresponde a quantidade multiplicada pelo preço");
        }

        // Como nenhum tributo conhecido incide nesta operação estritamente
        // delimitada e não há desconto, o valor comercial coincide com o valor
        // fiscal sem tributos. NUNCA estender esta igualdade a itens tributados.
        NfceLiquidProductValues values = new NfceLiquidProductValues(item.getUnitPrice(), total);
        values.validateQuantity(item.getQuantity());
        return values;
    }

    private static boolean eligibleRiceNcm(String ncm) {
        return ncm != null && ncm.matches("\\d{8}")
            && (ncm.startsWith("100620") || ncm.startsWith("100630") || "10064000".equals(ncm));
    }

    private static BigDecimal exactCents(BigDecimal value) {
        if (value == null) {
            throw unsupported("valor fiscal do item ausente");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw unsupported("item contém fração de centavo");
        }
    }

    private static boolean zero(BigDecimal value) {
        return value != null && value.signum() == 0;
    }

    private static boolean zeroOrAbsent(BigDecimal value) {
        return value == null || value.signum() == 0;
    }

    private static FiscalIntegrationException unsupported(String detail) {
        return new FiscalIntegrationException(
            "Produtor de vProdLiq ZERO-TAX não autoriza esta operação: " + detail + "."
        );
    }
}
