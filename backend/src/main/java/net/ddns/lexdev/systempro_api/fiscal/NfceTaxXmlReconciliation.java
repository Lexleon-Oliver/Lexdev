package net.ddns.lexdev.systempro_api.fiscal;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;

/**
 * Conciliação SOMENTE LEITURA do XML fiscal gerado com o snapshot da venda.
 *
 * Confere valores efetivamente SERIALIZADOS, independentemente da rotina que
 * calculou o imposto. Não modifica a venda, não depende de cadastros mutáveis,
 * não envia XML e NÃO demonstra quais tributos estão incorporados ao preço.
 *
 * Uso inicial: auditoria e regressão. Não é um produtor de vProdLiq.
 */
public final class NfceTaxXmlReconciliation {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final List<String> PIS_COFINS_NON_TAXED = List.of("04", "05", "06", "07", "08", "09");

    /** Não foi informado um contrato que prove tributos efetivamente embutidos em vProd. */
    public enum PriceInclusionStatus { NOT_DEMONSTRATED }

    public record Item(int number, NfceTaxCompositionEvidence evidence) { }

    public record Report(
        List<Item> items,
        BigDecimal grossProduct,
        BigDecimal discount,
        BigDecimal netCommercial,
        BigDecimal icms,
        BigDecimal pis,
        BigDecimal cofins,
        BigDecimal ibsUf,
        BigDecimal ibsMunicipal,
        BigDecimal cbs,
        boolean rtcPresent,
        PriceInclusionStatus priceInclusionStatus
    ) {
        public Report {
            items = List.copyOf(items);
        }
    }

    private NfceTaxXmlReconciliation() { }

    public static Report reconcile(Sale sale, TaxRegime regime, String xml) {
        if (sale == null || sale.getItems() == null || sale.getItems().isEmpty() || regime == null) {
            throw invalid("venda e regime são obrigatórios");
        }
        Element inf = requiredOne(parse(xml), "infNFe");
        List<Element> details = children(inf, "det");
        if (details.size() != sale.getItems().size()) {
            throw invalid("quantidade de itens do XML difere da venda");
        }
        Map<Integer, Element> xmlItems = new HashMap<>();
        for (Element det : details) {
            String number = det.getAttribute("nItem");
            if (!number.matches("[1-9][0-9]*")) throw invalid("nItem inválido");
            final int n;
            try {
                n = Integer.parseInt(number);
            } catch (NumberFormatException e) {
                throw invalid("nItem excede o limite inteiro");
            }
            if (xmlItems.putIfAbsent(n, det) != null) throw invalid("nItem duplicado no XML: " + n);
        }

        BigDecimal gross = ZERO, discount = ZERO, net = ZERO, icms = ZERO, pis = ZERO, cofins = ZERO;
        BigDecimal ibsUf = ZERO, ibsMunicipal = ZERO, cbs = ZERO, rtcBase = ZERO, icmsBase = ZERO;
        BigDecimal liquidTotal = ZERO;
        boolean anyRtc = false, anyLiquid = false;
        List<Item> reports = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();

        for (SaleItem item : sale.getItems()) {
            if (item == null || item.getItemNumber() <= 0 || !seen.add(item.getItemNumber())) {
                throw invalid("item fiscal ausente, inválido ou duplicado na venda");
            }
            Element det = xmlItems.get(item.getItemNumber());
            if (det == null) throw invalid("item " + item.getItemNumber() + " não encontrado no XML");
            NfceTaxCompositionEvidence evidence = NfceTaxCompositionEvidence.capture(item, regime);
            Element product = child(det, "prod");
            Element taxes = child(det, "imposto");
            String label = "item " + item.getItemNumber();

            equal(evidence.grossProduct(), amount(product, "vProd"), label + " vProd");
            equal(evidence.commercialDiscount(), optionalAmount(product, "vDesc"), label + " vDesc");
            equal(item.getQuantity(), decimal(product, "qCom"), label + " qCom");
            equal(item.getUnitPrice(), decimal(product, "vUnCom"), label + " vUnCom");
            compareIcms(item, regime, evidence, taxes, label);
            comparePisCofins(item.getPisCstSnapshot(), item.getPisRate(),
                evidence.netCommercial(), evidence.pisXml(), taxes, "PIS", label);
            comparePisCofins(item.getCofinsCstSnapshot(), item.getCofinsRate(),
                evidence.netCommercial(), evidence.cofinsXml(), taxes, "COFINS", label);
            compareRtc(item, evidence, taxes, label);

            Element liquidUnit = optionalChild(product, "vUnComLiq");
            Element liquidItem = optionalChild(product, "vProdLiq");
            if ((liquidUnit == null) != (liquidItem == null)) {
                throw invalid(label + " possui apenas parte dos campos líquidos");
            }
            if (liquidUnit != null) {
                NfceLiquidProductValues snapshot = item.getNfceLiquidSnapshot();
                if (snapshot == null) throw invalid(label + " possui valor líquido sem snapshot de origem");
                equal(snapshot.unitWithoutTaxes(), decimal(product, "vUnComLiq"), label + " vUnComLiq");
                equal(snapshot.totalWithoutTaxes(), amount(product, "vProdLiq"), label + " vProdLiq");
                anyLiquid = true;
                liquidTotal = liquidTotal.add(snapshot.totalWithoutTaxes());
            } else if (item.hasAnyNfceLiquidSnapshotData()) {
                // Emissão desativada é permitida: snapshot pode existir sem sair no XML.
                item.getNfceLiquidSnapshot(); // Ainda assim rejeita snapshot persistido incompleto.
            }

            reports.add(new Item(item.getItemNumber(), evidence));
            gross = gross.add(evidence.grossProduct());
            discount = discount.add(evidence.commercialDiscount());
            net = net.add(evidence.netCommercial());
            icms = icms.add(evidence.icmsXml());
            pis = pis.add(evidence.pisXml());
            cofins = cofins.add(evidence.cofinsXml());
            if ("00".equals(item.getIcmsCstCsosnSnapshot()) && !regime.usesCsosn()) {
                icmsBase = icmsBase.add(evidence.netCommercial());
            }
            if (evidence.rtcPresent()) {
                anyRtc = true;
                rtcBase = rtcBase.add(evidence.rtcTaxBase());
                ibsUf = ibsUf.add(evidence.ibsUfXml());
                ibsMunicipal = ibsMunicipal.add(evidence.ibsMunicipalXml());
                cbs = cbs.add(evidence.cbsXml());
            }
        }
        if (anyLiquid && reports.stream().anyMatch(i -> optionalChild(
                child(xmlItems.get(i.number()), "prod"), "vProdLiq") == null)) {
            throw invalid("campos líquidos presentes somente em parte dos itens");
        }

        Element total = child(inf, "total");
        Element icmsTot = child(total, "ICMSTot");
        equal(gross, amount(icmsTot, "vProd"), "total vProd");
        equal(discount, amount(icmsTot, "vDesc"), "total vDesc");
        equal(net, amount(icmsTot, "vNF"), "total vNF");
        equal(sale.getTotal(), net, "total comercial persistido");
        equal(icmsBase, amount(icmsTot, "vBC"), "total base ICMS");
        equal(icms, amount(icmsTot, "vICMS"), "total vICMS");
        equal(pis, amount(icmsTot, "vPIS"), "total vPIS");
        equal(cofins, amount(icmsTot, "vCOFINS"), "total vCOFINS");

        Element rtcTot = optionalChild(total, "IBSCBSTot");
        if ((rtcTot != null) != anyRtc) throw invalid("presença do total IBS/CBS diverge dos itens");
        if (anyRtc) {
            equal(rtcBase, amount(rtcTot, "vBCIBSCBS"), "total vBCIBSCBS");
            Element gIbs = child(rtcTot, "gIBS");
            equal(ibsUf, amount(child(gIbs, "gIBSUF"), "vIBSUF"), "total vIBSUF");
            equal(ibsMunicipal, amount(child(gIbs, "gIBSMun"), "vIBSMun"), "total vIBSMun");
            equal(ibsUf.add(ibsMunicipal), amount(gIbs, "vIBS"), "total vIBS");
            equal(cbs, amount(child(rtcTot, "gCBS"), "vCBS"), "total vCBS");
        }
        Element xmlLiquidTotal = optionalChild(total, "vProdLiqTot");
        if ((xmlLiquidTotal != null) != anyLiquid) {
            throw invalid("total de valores líquidos divergente dos itens");
        }
        if (anyLiquid) equal(liquidTotal, decimal(total, "vProdLiqTot"), "total vProdLiqTot");

        // Os montantes emitidos NÃO são uma prova de tributos embutidos no preço.
        // Nenhuma subtração de ICMS/PIS/COFINS/IBS/CBS é autorizada por este relatório.
        return new Report(reports, gross, discount, net, icms, pis, cofins,
            ibsUf, ibsMunicipal, cbs, anyRtc, PriceInclusionStatus.NOT_DEMONSTRATED);
    }

    private static void compareIcms(
        SaleItem item, TaxRegime regime, NfceTaxCompositionEvidence evidence,
        Element taxes, String label
    ) {
        Element root = child(taxes, "ICMS");
        String code = item.getIcmsCstCsosnSnapshot();
        String group = regime.usesCsosn() ? "ICMSSN" + code : ("00".equals(code) ? "ICMS00" : "ICMS40");
        Element detail = child(root, group);
        equalText(code, text(detail, regime.usesCsosn() ? "CSOSN" : "CST"), label + " ICMS CST/CSOSN");
        if (!regime.usesCsosn() && "00".equals(code)) {
            equal(evidence.netCommercial(), amount(detail, "vBC"), label + " ICMS vBC");
            equal(item.getIcmsRate(), decimal(detail, "pICMS"), label + " ICMS pICMS");
            equal(evidence.icmsXml(), amount(detail, "vICMS"), label + " ICMS vICMS");
        }
    }

    private static void comparePisCofins(
        String code, BigDecimal rate, BigDecimal taxBase, BigDecimal expected,
        Element taxes, String name, String label
    ) {
        Element root = child(taxes, name);
        boolean nonTaxed = PIS_COFINS_NON_TAXED.contains(code);
        Element detail = child(root, name + (nonTaxed ? "NT" : "Aliq"));
        equalText(code, text(detail, "CST"), label + " " + name + " CST");
        if (!nonTaxed) {
            equal(taxBase, amount(detail, "vBC"), label + " " + name + " vBC");
            equal(rate, decimal(detail, "p" + name), label + " " + name + " p" + name);
            equal(expected, amount(detail, "v" + name), label + " v" + name);
        }
    }

    private static void compareRtc(SaleItem item, NfceTaxCompositionEvidence evidence, Element taxes, String label) {
        Element rtc = optionalChild(taxes, "IBSCBS");
        if (!evidence.rtcPresent()) {
            if (rtc != null) throw invalid(label + " emite RTC sem snapshot");
            return;
        }
        if (rtc == null) throw invalid(label + " omitiu RTC persistido");
        equalText(item.getIbsCbsCstSnapshot(), text(rtc, "CST"), label + " RTC CST");
        equalText(item.getCClassTribSnapshot(), text(rtc, "cClassTrib"), label + " RTC cClassTrib");
        Element group = child(rtc, "gIBSCBS");
        equal(evidence.rtcTaxBase(), amount(group, "vBC"), label + " RTC vBC");
        equal(evidence.ibsUfXml(), amount(child(group, "gIBSUF"), "vIBSUF"), label + " RTC vIBSUF");
        equal(evidence.ibsMunicipalXml(), amount(child(group, "gIBSMun"), "vIBSMun"), label + " RTC vIBSMun");
        equal(evidence.ibsUfXml().add(evidence.ibsMunicipalXml()), amount(group, "vIBS"), label + " RTC vIBS");
        equal(evidence.cbsXml(), amount(child(group, "gCBS"), "vCBS"), label + " RTC vCBS");
        // Tributação integral: as alíquotas serializadas devem reproduzir as
        // efetivas do snapshot. Reduções exigem comparação nominal separada.
        if ("000001".equals(item.getCClassTribSnapshot())) {
            equal(item.getIbsUfRate(), decimal(child(group, "gIBSUF"), "pIBSUF"), label + " RTC pIBSUF");
            equal(item.getIbsMunicipalRate(), decimal(child(group, "gIBSMun"), "pIBSMun"), label + " RTC pIBSMun");
            equal(item.getCbsRateRtc(), decimal(child(group, "gCBS"), "pCBS"), label + " RTC pCBS");
        }
        if ("200003".equals(item.getCClassTribSnapshot())) {
            // A alíquota no snapshot é EFETIVA (zero); o XML traz a NOMINAL.
            // Esta redução deve estar explícita em cada jurisdição.
            for (String name : List.of("gIBSUF", "gIBSMun", "gCBS")) {
                Element red = child(child(group, name), "gRed");
                equal(new BigDecimal("100"), decimal(red, "pRedAliq"), label + " " + name + " pRedAliq");
                equal(BigDecimal.ZERO, decimal(red, "pAliqEfet"), label + " " + name + " pAliqEfet");
            }
        }
    }

    private static Document parse(String xml) {
        if (xml == null || xml.isBlank()) throw invalid("XML ausente");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            // Evita imprimir conteúdo XML malformado no stderr ou nos logs fiscais.
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void fatalError(SAXParseException exception) throws SAXParseException {
                    throw exception;
                }
            });
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new FiscalIntegrationException("XML NFC-e inválido ou inseguro para conciliação tributária.", e);
        }
    }

    private static Element requiredOne(Document doc, String name) {
        NodeList nodes = doc.getElementsByTagNameNS("*", name);
        if (nodes.getLength() != 1 || !(nodes.item(0) instanceof Element element)) {
            throw invalid("estrutura XML exige exatamente um " + name);
        }
        return element;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> found = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && name.equals(e.getLocalName() == null ? e.getTagName() : e.getLocalName())) {
                found.add(e);
            }
        }
        return found;
    }

    private static Element optionalChild(Element parent, String name) {
        List<Element> matches = children(parent, name);
        if (matches.size() > 1) throw invalid("campo XML duplicado: " + name);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static Element child(Element parent, String name) {
        Element result = optionalChild(parent, name);
        if (result == null) throw invalid("campo XML ausente: " + name);
        return result;
    }

    private static String text(Element parent, String name) {
        return child(parent, name).getTextContent().trim();
    }

    private static BigDecimal amount(Element parent, String name) {
        return decimal(parent, name);
    }

    private static BigDecimal optionalAmount(Element parent, String name) {
        Element value = optionalChild(parent, name);
        return value == null ? ZERO : parseDecimal(value.getTextContent(), name);
    }

    private static BigDecimal decimal(Element parent, String name) {
        return parseDecimal(text(parent, name), name);
    }

    private static BigDecimal parseDecimal(String value, String field) {
        String trimmed = value.trim();
        if (!trimmed.matches("[0-9]+(\\.[0-9]+)?")) throw invalid("valor XML não numérico ou negativo: " + field);
        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            throw invalid("valor XML inválido: " + field);
        }
    }

    private static void equal(BigDecimal expected, BigDecimal found, String name) {
        if (expected == null || found == null || expected.compareTo(found) != 0) {
            throw invalid(name + " diverge: snapshot=" + expected + ", XML=" + found);
        }
    }

    private static void equalText(String expected, String found, String name) {
        if (expected == null || !expected.equals(found)) {
            throw invalid(name + " diverge do snapshot");
        }
    }

    private static FiscalIntegrationException invalid(String detail) {
        return new FiscalIntegrationException("Conciliação tributária NFC-e V2: " + detail + ".");
    }
}
