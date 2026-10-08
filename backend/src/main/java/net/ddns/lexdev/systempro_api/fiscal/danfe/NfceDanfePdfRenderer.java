package net.ddns.lexdev.systempro_api.fiscal.danfe;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;

@Component
public class NfceDanfePdfRenderer {
    private static final float MM = 72f / 25.4f;
    private static final float PAGE_WIDTH = 80f * MM;
    private static final float MARGIN = 2f * MM;
    private static final float QR_SIZE = 25f * MM;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final PDFont NORMAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public byte[] render(FiscalDocument fiscalDocument) {
        try {
            DanfeData data = parse(fiscalDocument);
            float pageHeight = Math.max(210f * MM, (150 + data.items().size() * 8 + data.payments().size() * 5) * MM);
            try (PDDocument pdf = new PDDocument()) {
                PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, pageHeight));
                pdf.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                    Cursor c = new Cursor(pageHeight - MARGIN);
                    centered(cs, c, data.issuerName(), BOLD, 9);
                    centered(cs, c, "CNPJ: " + formatDocument(data.issuerCnpj()), NORMAL, 7);
                    if (!data.issuerAddress().isBlank()) centered(cs, c, data.issuerAddress(), NORMAL, 6.5f);
                    separator(cs, c);
                    centered(cs, c, "DANFE NFC-e", BOLD, 9);
                    centered(cs, c, "Documento Auxiliar da Nota Fiscal", NORMAL, 6.5f);
                    centered(cs, c, "de Consumidor Eletronica", NORMAL, 6.5f);
                    centered(cs, c, "Nao permite aproveitamento de credito de ICMS", NORMAL, 6);
                    if (data.contingency()) {
                        separator(cs, c);
                        centered(cs, c, "EMITIDA EM CONTINGÊNCIA", BOLD, 9);
                        centered(cs, c, "Pendente de autorizacao", BOLD, 7);
                        if (!data.contingencyAt().isBlank()) {
                            centered(cs, c, "Entrada em contingencia: " + data.contingencyAt(), NORMAL, 6.5f);
                        }
                        for (String line : wrap("Justificativa: " + data.contingencyJustification(), 52)) {
                            centered(cs, c, line, NORMAL, 6);
                        }
                    }
                    separator(cs, c);

                    text(cs, c, "COD  DESCRICAO", BOLD, 6.2f);
                    text(cs, c, "QTD x VL.UNIT.                 VL.TOTAL", BOLD, 6.2f);
                    for (Item item : data.items()) {
                        text(cs, c, fit(item.code() + "  " + item.description(), 52), NORMAL, 6.2f);
                        text(cs, c, item.quantity() + " " + item.unit() + " x " + money(item.unitPrice())
                            + "                 " + money(item.total()), NORMAL, 6.2f);
                    }
                    separator(cs, c);
                    text(cs, c, "Qtd. total de itens: " + data.items().size(), NORMAL, 7);
                    right(cs, c, "VALOR TOTAL R$ " + money(data.total()), BOLD, 8);
                    if (data.discount().compareTo(BigDecimal.ZERO) > 0) {
                        right(cs, c, "Desconto R$ " + money(data.discount()), NORMAL, 7);
                    }
                    separator(cs, c);
                    text(cs, c, "FORMA DE PAGAMENTO", BOLD, 6.5f);
                    for (Payment payment : data.payments()) {
                        text(cs, c, payment.label() + "  R$ " + money(payment.amount()), NORMAL, 6.5f);
                    }
                    separator(cs, c);
                    centered(cs, c, "Consulte pela Chave de Acesso em", NORMAL, 6.5f);
                    centered(cs, c, data.consultationUrl(), NORMAL, 5.5f);
                    centered(cs, c, groupKey(data.accessKey()), BOLD, 6.5f);
                    centered(cs, c, "NFC-e n. " + data.number() + " Serie " + data.series(), NORMAL, 7);
                    centered(cs, c, "Emissao: " + data.emissionDate(), NORMAL, 6.5f);
                    if (data.contingency()) {
                        centered(cs, c, "EMITIDA EM CONTINGÊNCIA - Pendente de autorizacao", BOLD, 6.5f);
                    } else {
                        centered(cs, c, "Protocolo: " + data.protocol(), NORMAL, 6.5f);
                    }
                    separator(cs, c);
                    centered(cs, c, data.consumer(), BOLD, 6.5f);

                    byte[] qrPng = qrPng(data.qrCode());
                    PDImageXObject qr = PDImageXObject.createFromByteArray(pdf, qrPng, "nfce-qrcode");
                    c.y -= 2 * MM;
                    float x = (PAGE_WIDTH - QR_SIZE) / 2;
                    cs.drawImage(qr, x, c.y - QR_SIZE, QR_SIZE, QR_SIZE);
                    c.y -= QR_SIZE + 3 * MM;
                    centered(cs, c, "Consulta via leitor de QR Code", NORMAL, 6.5f);
                    if (!data.additionalInfo().isBlank()) {
                        separator(cs, c);
                        for (String line : wrap(data.additionalInfo(), 52)) text(cs, c, line, NORMAL, 6);
                    }
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                pdf.save(out);
                return out.toByteArray();
            }
        } catch (FiscalIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new FiscalIntegrationException("Não foi possível gerar o DANFE NFC-e.", ex);
        }
    }

    DanfeData parse(FiscalDocument fiscalDocument) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var doc = factory.newDocumentBuilder().parse(new InputSource(new java.io.StringReader(fiscalDocument.getXml())));

            Element emit = first(doc, "emit");
            Element ender = optionalFirst(emit, "enderEmit");
            Element ide = first(doc, "ide");
            Element total = first(doc, "ICMSTot");
            List<Item> items = new ArrayList<>();
            NodeList dets = doc.getElementsByTagNameNS("*", "det");
            for (int i = 0; i < dets.getLength(); i++) {
                Element prod = first((Element) dets.item(i), "prod");
                items.add(new Item(value(prod,"cProd"), value(prod,"xProd"), value(prod,"qCom"),
                    value(prod,"uCom"), decimal(prod,"vUnCom"), decimal(prod,"vProd")));
            }
            List<Payment> payments = new ArrayList<>();
            NodeList detPag = doc.getElementsByTagNameNS("*", "detPag");
            for (int i = 0; i < detPag.getLength(); i++) {
                Element p = (Element) detPag.item(i);
                payments.add(new Payment(paymentLabel(value(p,"tPag")), decimal(p,"vPag")));
            }
            String dhEmi = value(ide, "dhEmi");
            String emission = dhEmi;
            try { emission = OffsetDateTime.parse(dhEmi).format(DATE_TIME); } catch (Exception ignored) { }
            String consumerDoc = optionalValue(doc, "CPF");
            if (consumerDoc.isBlank()) {
                Element dest = optionalFirst(doc.getDocumentElement(), "dest");
                consumerDoc = dest == null ? "" : valueOptional(dest, "CNPJ");
            }
            String consumer = consumerDoc.isBlank() ? "CONSUMIDOR NAO IDENTIFICADO" : "CONSUMIDOR: " + formatDocument(consumerDoc);
            boolean contingency = "9".equals(valueOptional(ide, "tpEmis"));
            String contingencyAt = "";
            String contingencyJustification = "";
            if (contingency) {
                String dhCont = value(ide, "dhCont");
                contingencyAt = dhCont;
                try { contingencyAt = OffsetDateTime.parse(dhCont).format(DATE_TIME); } catch (Exception ignored) { }
                contingencyJustification = value(ide, "xJust");
            }
            String issuerAddress = ender == null ? "" : String.join(", ", nonBlank(
                valueOptional(ender,"xLgr") + " " + valueOptional(ender,"nro"),
                valueOptional(ender,"xBairro"),
                valueOptional(ender,"xMun") + "/" + valueOptional(ender,"UF")));

            return new DanfeData(value(emit,"xNome"), value(emit,"CNPJ"), issuerAddress,
                fiscalDocument.getAccessKey(), value(ide,"nNF"), value(ide,"serie"), emission,
                fiscalDocument.getProtocol(), decimal(total,"vNF"), decimal(total,"vDesc"), items, payments,
                value(first(doc,"infNFeSupl"),"qrCode"), value(first(doc,"infNFeSupl"),"urlChave"),
                optionalValue(doc,"infCpl"), consumer, contingency, contingencyAt, contingencyJustification);
        } catch (Exception ex) {
            throw new FiscalIntegrationException("O XML da NFC-e não pôde ser interpretado para gerar o DANFE.", ex);
        }
    }

    private static byte[] qrPng(String content) throws Exception {
        var hints = java.util.Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 4);
        BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 300, 300, hints);
        BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        for (int y=0; y<matrix.getHeight(); y++) for (int x=0; x<matrix.getWidth(); x++) image.setRGB(x,y,matrix.get(x,y)?0xFF000000:0xFFFFFFFF);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image,"png",out); return out.toByteArray();
    }
    private static void text(PDPageContentStream cs, Cursor c, String s, PDFont f, float size) throws Exception { draw(cs,c,s,f,size,MARGIN); }
    private static void centered(PDPageContentStream cs, Cursor c, String s, PDFont f, float size) throws Exception { float w=f.getStringWidth(safe(s))*size/1000f; draw(cs,c,s,f,size,Math.max(MARGIN,(PAGE_WIDTH-w)/2)); }
    private static void right(PDPageContentStream cs, Cursor c, String s, PDFont f, float size) throws Exception { float w=f.getStringWidth(safe(s))*size/1000f; draw(cs,c,s,f,size,Math.max(MARGIN,PAGE_WIDTH-MARGIN-w)); }
    private static void draw(PDPageContentStream cs, Cursor c, String s, PDFont f, float size, float x) throws Exception { cs.beginText(); cs.setFont(f,size); cs.newLineAtOffset(x,c.y); cs.showText(safe(s)); cs.endText(); c.y-=size+2.2f; }
    private static void separator(PDPageContentStream cs, Cursor c) throws Exception { c.y-=2; cs.moveTo(MARGIN,c.y); cs.lineTo(PAGE_WIDTH-MARGIN,c.y); cs.setLineWidth(.35f); cs.stroke(); c.y-=5; }
    private static String safe(String s) { if(s==null)return ""; return s.replace('–','-').replace('—','-').replace('“','\"').replace('”','\"').replace('’','\''); }
    private static String fit(String s,int n){ return s.length()<=n?s:s.substring(0,n-1)+"…"; }
    private static List<String> wrap(String s,int n){ List<String> r=new ArrayList<>(); String t=s; while(t.length()>n){int p=t.lastIndexOf(' ',n); if(p<1)p=n;r.add(t.substring(0,p));t=t.substring(p).trim();} if(!t.isBlank())r.add(t); return r; }
    private static String money(BigDecimal v){ return String.format(java.util.Locale.of("pt","BR"),"%,.2f",v); }
    private static String groupKey(String k){ return k.replaceAll("(\\d{4})(?=\\d)","$1 "); }
    private static String formatDocument(String d){ if(d==null)return ""; if(d.length()==14)return d.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})","$1.$2.$3/$4-$5"); if(d.length()==11)return d.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})","$1.$2.$3-$4"); return d; }
    private static String[] nonBlank(String...v){ return java.util.Arrays.stream(v).map(String::trim).filter(x->!x.isBlank()&&!x.equals("/")).toArray(String[]::new); }
    private static Element first(org.w3c.dom.Document d,String n){ Element e=optionalFirst(d.getDocumentElement(),n); if(e==null)throw new IllegalArgumentException("Elemento "+n+" ausente"); return e; }
    private static Element first(Element root,String n){ Element e=optionalFirst(root,n); if(e==null)throw new IllegalArgumentException("Elemento "+n+" ausente"); return e; }
    private static Element optionalFirst(Element root,String n){ NodeList l=root.getElementsByTagNameNS("*",n); return l.getLength()==0?null:(Element)l.item(0); }
    private static String value(Element e,String n){ String v=valueOptional(e,n); if(v.isBlank())throw new IllegalArgumentException("Elemento "+n+" vazio"); return v; }
    private static String valueOptional(Element e,String n){ Element x=optionalFirst(e,n); return x==null?"":x.getTextContent().trim(); }
    private static String optionalValue(org.w3c.dom.Document d,String n){ NodeList l=d.getElementsByTagNameNS("*",n); return l.getLength()==0?"":l.item(0).getTextContent().trim(); }
    private static BigDecimal decimal(Element e,String n){ String v=valueOptional(e,n); return v.isBlank()?BigDecimal.ZERO:new BigDecimal(v); }
    private static String paymentLabel(String code){ return switch(code){case "01"->"Dinheiro";case "03"->"Cartao de credito";case "04"->"Cartao de debito";case "17"->"PIX";default->"Pagamento ("+code+")";}; }
    private static final class Cursor { float y; Cursor(float y){this.y=y;} }
    record Item(String code,String description,String quantity,String unit,BigDecimal unitPrice,BigDecimal total){}
    record Payment(String label,BigDecimal amount){}
    record DanfeData(String issuerName,String issuerCnpj,String issuerAddress,String accessKey,String number,String series,String emissionDate,String protocol,BigDecimal total,BigDecimal discount,List<Item> items,List<Payment> payments,String qrCode,String consultationUrl,String additionalInfo,String consumer,boolean contingency,String contingencyAt,String contingencyJustification){}
}