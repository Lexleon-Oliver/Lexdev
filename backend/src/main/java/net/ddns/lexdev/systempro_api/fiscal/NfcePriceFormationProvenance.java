package net.ddns.lexdev.systempro_api.fiscal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;

/**
 * Contrato de rastreabilidade da FORMAÇÃO DO PREÇO, separado do cálculo fiscal.
 *
 * A V3 apenas confere uma declaração de formação do preço com os montantes de
 * uma NFC-e já conciliada pela V2. Nenhum atributo deste contrato demonstra,
 * por si só, a autenticidade da origem declarada ou a exaustividade dos tributos
 * embutidos. Não é um produtor de vProdLiq, vUnComLiq ou vProdLiqTot.
 *
 * Valores aqui declarados são pós-desconto. Não reinterpretar sua parcela
 * "base" como vProdLiq: a regra fiscal de alocação dos descontos e tributos
 * ainda depende de um produtor tributário com origem comprovada.
 */
public final class NfcePriceFormationProvenance {

    /** Apenas tributos potencialmente incorporáveis ao preço de vProd.
     * IBS, CBS e IS não são componentes de vProd pela NT 2026.008 v1.01. */
    public enum EmbeddedTax { ICMS, PIS, COFINS, IPI, ICMS_ST, ISS, OTHER }

    public enum Status { SOURCE_MISSING, ARITHMETICALLY_RECONCILED_NOT_VERIFIED }

    /** Referência DECLARADA a um registro de formação de preço, não autenticada. */
    public record Source(String system, String reference, String version, String sha256) {
        public Source {
            requireIdentifier(system, "sistema de origem");
            requireIdentifier(reference, "referência de origem");
            requireVersion(version);
            if (sha256 == null || !sha256.matches("[a-f0-9]{64}")) {
                throw invalid("SHA-256 da origem deve ter 64 caracteres hexadecimais minúsculos");
            }
        }
    }

    /** Um tipo explícito para cada tributo; zero declarado não equivale a ausência demonstrada. */
    public record TaxPart(EmbeddedTax type, BigDecimal includedAfterDiscount) {
        public TaxPart {
            if (type == null) throw invalid("tipo tributário não informado");
            includedAfterDiscount = nonNegativeCents(includedAfterDiscount, "parcela " + type);
        }
    }

    /** A soma de base declarada e parcelas declaradas deve conciliar com o preço comercial. */
    public record ItemDeclaration(int itemNumber, BigDecimal baseAfterDiscount, List<TaxPart> parts) {
        public ItemDeclaration {
            if (itemNumber <= 0) throw invalid("número do item de formação de preço inválido");
            baseAfterDiscount = nonNegativeCents(baseAfterDiscount, "base pós-desconto");
            if (parts == null) throw invalid("parcelas tributárias não informadas");
            EnumMap<EmbeddedTax, BigDecimal> seen = new EnumMap<>(EmbeddedTax.class);
            for (TaxPart part : parts) {
                if (part == null || seen.putIfAbsent(part.type(), part.includedAfterDiscount()) != null) {
                    throw invalid("parcela tributária nula ou duplicada no item " + itemNumber);
                }
            }
            parts = List.copyOf(parts);
            // Nem omissão nem "zero implícito" significam prova de não-incidência.
            if (seen.size() != EmbeddedTax.values().length) {
                throw invalid("item " + itemNumber + " precisa declarar explicitamente todos os tipos de parcelas");
            }
        }
    }

    /** Vincula a declaração a um conjunto de montantes fiscais já conciliados. */
    public record Declaration(Source source, String reconciliationFingerprint, List<ItemDeclaration> items) {
        public Declaration {
            if (source == null) throw invalid("origem da formação de preço não informada");
            if (reconciliationFingerprint == null || !reconciliationFingerprint.matches("[a-f0-9]{64}")) {
                throw invalid("fingerprint da conciliação inválido");
            }
            if (items == null || items.isEmpty()) throw invalid("declaração de preço sem itens");
            for (ItemDeclaration item : items) {
                if (item == null) throw invalid("declaração de preço contém item nulo");
            }
            items = List.copyOf(items);
        }
    }

    /** Comparação informativa; imposto apurado no XML não prova parcela embutida. */
    public record TaxComparison(EmbeddedTax tax, BigDecimal declaredIncluded,
                                BigDecimal reportedInXml, boolean equalAmounts) { }

    public record ItemAssessment(int itemNumber, BigDecimal commercialAfterDiscount,
                                 BigDecimal declaredBase, BigDecimal declaredTaxParts,
                                 List<TaxComparison> comparisons) {
        public ItemAssessment {
            comparisons = List.copyOf(comparisons);
        }
    }

    public record Assessment(Status status, String reconciliationFingerprint,
                             List<ItemAssessment> items, List<String> blockers) {
        public Assessment {
            items = List.copyOf(items);
            blockers = List.copyOf(blockers);
        }

        /** Contrato explicitamente incapaz de autorizar qualquer snapshot líquido. */
        public boolean authorizesLiquidValues() { return false; }
    }

    private NfcePriceFormationProvenance() { }

    /**
     * Fingerprint determinístico dos montantes reconciliados (não é assinatura
     * digital, identificação da venda, nem hash completo do XML). Não pode ser
     * usado como prova de autenticidade da formação de preço.
     */
    public static String reconciliationFingerprint(NfceTaxXmlReconciliation.Report report) {
        checkReport(report);
        StringBuilder canonical = new StringBuilder("SYSTEMPRO:NFCE:PRICE-TRACE:V3\n");
        List<NfceTaxXmlReconciliation.Item> sorted = report.items().stream()
            .sorted(Comparator.comparingInt(NfceTaxXmlReconciliation.Item::number)).toList();
        for (var item : sorted) {
            var e = item.evidence();
            canonical.append(item.number()).append('|')
                .append(money(e.grossProduct())).append('|')
                .append(money(e.commercialDiscount())).append('|')
                .append(money(e.netCommercial())).append('|')
                .append(money(e.icmsXml())).append('|')
                .append(money(e.pisXml())).append('|')
                .append(money(e.cofinsXml())).append('|')
                .append(e.rtcPresent()).append('|')
                .append(nullableMoney(e.rtcTaxBase())).append('|')
                .append(nullableMoney(e.ibsUfXml())).append('|')
                .append(nullableMoney(e.ibsMunicipalXml())).append('|')
                .append(nullableMoney(e.cbsXml())).append('\n');
        }
        canonical.append("TOTAL|").append(money(report.grossProduct())).append('|')
            .append(money(report.discount())).append('|').append(money(report.netCommercial())).append('|')
            .append(money(report.icms())).append('|').append(money(report.pis())).append('|')
            .append(money(report.cofins())).append('|').append(money(report.ibsUf())).append('|')
            .append(money(report.ibsMunicipal())).append('|').append(money(report.cbs()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /**
     * Concilia uma declaração de preço com o relatório da V2. Omitir a origem
     * retorna bloqueio explícito. Informação contraditória falha imediatamente.
     */
    public static Assessment assess(NfceTaxXmlReconciliation.Report report, Declaration declaration) {
        String fingerprint = reconciliationFingerprint(report);
        if (declaration == null) {
            return new Assessment(Status.SOURCE_MISSING, fingerprint, List.of(), List.of(
                "Registro versionado de formação de preço não fornecido.",
                "Tributos apurados no XML não demonstram valores embutidos no preço.",
                "Nenhum campo líquido pode ser produzido a partir desta avaliação."
            ));
        }
        if (!fingerprint.equals(declaration.reconciliationFingerprint())) {
            throw invalid("declaração não corresponde aos montantes fiscais reconciliados");
        }
        Map<Integer, ItemDeclaration> declared = new HashMap<>();
        for (ItemDeclaration item : declaration.items()) {
            if (item == null || declared.putIfAbsent(item.itemNumber(), item) != null) {
                throw invalid("item de preço nulo ou duplicado");
            }
        }
        if (declared.size() != report.items().size()) {
            throw invalid("quantidade de itens não concilia com a venda");
        }
        List<ItemAssessment> evaluations = new ArrayList<>();
        Set<Integer> matched = new HashSet<>();
        for (var xmlItem : report.items()) {
            ItemDeclaration item = declared.get(xmlItem.number());
            if (item == null || !matched.add(xmlItem.number())) {
                throw invalid("item de formação ausente ou duplicado na conciliação");
            }
            var e = xmlItem.evidence();
            BigDecimal includedTotal = BigDecimal.ZERO.setScale(2);
            EnumMap<EmbeddedTax, BigDecimal> byTax = new EnumMap<>(EmbeddedTax.class);
            for (TaxPart part : item.parts()) {
                byTax.put(part.type(), part.includedAfterDiscount());
                includedTotal = includedTotal.add(part.includedAfterDiscount());
            }
            BigDecimal net = nonNegativeCents(e.netCommercial(), "total fiscal do item");
            if (item.baseAfterDiscount().add(includedTotal).compareTo(net) != 0) {
                throw invalid("item " + item.itemNumber() + " não fecha base + tributos com o valor comercial");
            }
            List<TaxComparison> compares = List.of(
                comparison(EmbeddedTax.ICMS, byTax, e.icmsXml()),
                comparison(EmbeddedTax.PIS, byTax, e.pisXml()),
                comparison(EmbeddedTax.COFINS, byTax, e.cofinsXml())
            );
            evaluations.add(new ItemAssessment(item.itemNumber(), net,
                item.baseAfterDiscount(), includedTotal, compares));
        }
        evaluations.sort(Comparator.comparingInt(ItemAssessment::itemNumber));
        return new Assessment(Status.ARITHMETICALLY_RECONCILED_NOT_VERIFIED,
            fingerprint, evaluations, List.of(
                "A referência e o SHA-256 declarados não foram autenticados contra um registro confiável de formação de preço.",
                "Parcelas declaradas, inclusive zeros, não comprovam incidência ou inclusão efetiva dos tributos no preço.",
                "A composição pós-desconto não autoriza calcular vProdLiq ou vUnComLiq.",
                "Conciliação não cobre, por si só, todos os regimes e ajustes fiscais aplicáveis."
            ));
    }

    private static TaxComparison comparison(EmbeddedTax tax, Map<EmbeddedTax, BigDecimal> parts,
                                            BigDecimal reported) {
        BigDecimal declared = parts.get(tax);
        return new TaxComparison(tax, declared, reported, reported != null && declared.compareTo(reported) == 0);
    }

    private static void checkReport(NfceTaxXmlReconciliation.Report report) {
        if (report == null || report.items() == null || report.items().isEmpty()) {
            throw invalid("relatório V2 ausente ou sem itens");
        }
        Set<Integer> numbers = new HashSet<>();
        for (var item : report.items()) {
            if (item == null || item.number() <= 0 || item.evidence() == null || !numbers.add(item.number())) {
                throw invalid("item V2 ausente, inválido ou duplicado");
            }
        }
        // Relatório V2 é o contrato de entrada; ele deve vir do método reconcile().
        // Não há validação da procedência de objetos construídos manualmente.
    }

    private static String nullableMoney(BigDecimal value) {
        return value == null ? "ABSENT" : money(value);
    }

    private static String money(BigDecimal amount) {
        return nonNegativeCents(amount, "montante fiscal").toPlainString();
    }

    private static BigDecimal nonNegativeCents(BigDecimal amount, String field) {
        if (amount == null || amount.signum() < 0) throw invalid(field + " negativo ou ausente");
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw invalid(field + " com fração de centavo");
        }
    }

    private static void requireVersion(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")) {
            throw invalid("versão da origem ausente ou fora do formato técnico");
        }
    }

    private static void requireIdentifier(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{2,127}")) {
            throw invalid(field + " ausente ou fora do formato técnico");
        }
    }

    private static FiscalIntegrationException invalid(String detail) {
        return new FiscalIntegrationException("Formação de preço NFC-e V3: " + detail + ".");
    }
}
