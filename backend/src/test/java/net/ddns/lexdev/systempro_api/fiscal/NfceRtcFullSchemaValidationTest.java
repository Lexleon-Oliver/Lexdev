package net.ddns.lexdev.systempro_api.fiscal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.StringReader;
import java.io.StringWriter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import net.ddns.lexdev.systempro_api.config.NfceSchemaProperties;
import net.ddns.lexdev.systempro_api.domain.AuditableEntity;
import net.ddns.lexdev.systempro_api.domain.Company;
import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.LegalEntity;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.PersonAddress;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.domain.SalePayment;
import net.ddns.lexdev.systempro_api.enums.AddressType;
import net.ddns.lexdev.systempro_api.enums.FiscalEnvironment;
import net.ddns.lexdev.systempro_api.enums.PaymentMethod;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsCalculation;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsJurisdictionCalculation;
import net.ddns.lexdev.systempro_api.fiscal.validation.NfceSchemaValidator;

class NfceRtcFullSchemaValidationTest {

    private static final String CERTIFICATE_PASSWORD = "changeit";

    @TempDir
    Path tempDir;

    @Test
    void deveValidarNfceRtcCompletaComMultiplosItensEDescontoNoPl010f() throws Exception {
        FiscalEstablishment establishment = establishment();
        Sale sale = new Sale();
        sale.setFiscalEstablishment(establishment);
        sale.setSubtotal(new BigDecimal("100.0000"));
        sale.setDiscount(new BigDecimal("1.0000"));
        sale.setTotal(new BigDecimal("99.0000"));

        sale.addItem(item(
            1, "P1", "60.0000", "1.0000", "59.0000",
            "59.00", "0.06", "0.00", "0.06", "0.53"
        ));
        sale.addItem(item(
            2, "P2", "40.0000", "0.0000", "40.0000",
            "40.00", "0.04", "0.00", "0.04", "0.36"
        ));

        SalePayment payment = new SalePayment();
        payment.setPaymentMethod(PaymentMethod.DINHEIRO);
        payment.setAmount(new BigDecimal("99.0000"));
        sale.addPayment(payment);

        FiscalDocument document = new FiscalDocument();
        document.setSale(sale);
        document.setEstablishment(establishment);
        document.setSeries(1);
        document.setNumber(1L);
        setId(document, 1L);

        String xml = SefazMgNfceGateway.NfceXmlBuilder.build(
            establishment,
            sale,
            document,
            "31261012345678000195650010000000011000000010",
            "00000001",
            1,
            "2026-10-08T00:00:00-03:00",
            "4.00",
            "SystemPro 1.0",
            "https://portalsped.fazenda.mg.gov.br/portalnfce/sistema/qrcode.xhtml?p=31261012345678000195650010000000011000000010|3|2",
            "https://portalsped.fazenda.mg.gov.br/portalnfce/sistema/consultaarg.xhtml"
        );

        assertTrue(xml.contains("<IBSCBS><CST>000</CST><cClassTrib>000001</cClassTrib>"));
        assertTrue(xml.contains("<vBCIBSCBS>99.00</vBCIBSCBS>"));
        assertTrue(xml.contains("<vIBSUF>0.10</vIBSUF>"));
        assertTrue(xml.contains("<vIBSMun>0.00</vIBSMun>"));
        assertTrue(xml.contains("<vIBS>0.10</vIBS>"));
        assertTrue(xml.contains("<vCBS>0.89</vCBS>"));

        NfceSchemaValidator validator = new NfceSchemaValidator(
            new DefaultResourceLoader(),
            new NfceSchemaProperties(
                "classpath:/fiscal/nfce/PL_010f/nfe_v4.00.xsd"
            )
        );

        String signedXml = signForSchemaValidation(xml);
        assertDoesNotThrow(() -> validator.validate(signedXml));
    }

    private String signForSchemaValidation(String xml) throws Exception {
        DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
        documentFactory.setNamespaceAware(true);
        Document document = documentFactory.newDocumentBuilder()
            .parse(new InputSource(new StringReader(xml)));

        Element infNFe = (Element) document
            .getElementsByTagNameNS("http://www.portalfiscal.inf.br/nfe", "infNFe")
            .item(0);
        infNFe.setIdAttribute("Id", true);

        Path p12 = generateCertificate();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(p12)) {
            keyStore.load(in, CERTIFICATE_PASSWORD.toCharArray());
        }
        String alias = keyStore.aliases().nextElement();
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(
            alias,
            CERTIFICATE_PASSWORD.toCharArray()
        );
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);

        XMLSignatureFactory signatureFactory = XMLSignatureFactory.getInstance("DOM");
        Reference reference = signatureFactory.newReference(
            "#" + infNFe.getAttribute("Id"),
            signatureFactory.newDigestMethod(DigestMethod.SHA1, null),
            List.of(
                signatureFactory.newTransform(
                    Transform.ENVELOPED,
                    (TransformParameterSpec) null
                ),
                signatureFactory.newTransform(
                    javax.xml.crypto.dsig.CanonicalizationMethod.INCLUSIVE,
                    (TransformParameterSpec) null
                )
            ),
            null,
            null
        );
        SignedInfo signedInfo = signatureFactory.newSignedInfo(
            signatureFactory.newCanonicalizationMethod(
                javax.xml.crypto.dsig.CanonicalizationMethod.INCLUSIVE,
                (C14NMethodParameterSpec) null
            ),
            signatureFactory.newSignatureMethod(SignatureMethod.RSA_SHA1, null),
            List.of(reference)
        );

        DOMSignContext context = new DOMSignContext(
            privateKey,
            infNFe.getParentNode()
        );
        context.setDefaultNamespacePrefix("ds");
        context.setIdAttributeNS(infNFe, null, "Id");

        var keyInfoFactory = signatureFactory.getKeyInfoFactory();
        var x509Data = keyInfoFactory.newX509Data(List.of(certificate));
        var keyInfo = keyInfoFactory.newKeyInfo(List.of(x509Data));

        XMLSignature signature = signatureFactory.newXMLSignature(signedInfo, keyInfo);
        signature.sign(context);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        var transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    private Path generateCertificate() throws Exception {
        Path output = tempDir.resolve("nfce-schema-test.p12");
        Path keytool = Path.of(
            System.getProperty("java.home"),
            "bin",
            System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool"
        );

        var command = new java.util.ArrayList<String>();
        command.add(keytool.toString());
        command.add("-genkeypair");
        command.add("-alias");
        command.add("nfce-schema");
        command.add("-keyalg");
        command.add("RSA");
        command.add("-keysize");
        command.add("2048");
        command.add("-dname");
        command.add("CN=SystemPro NFCe Schema Test, O=Lexdev, C=BR");
        command.add("-storetype");
        command.add("PKCS12");
        command.add("-keystore");
        command.add(output.toString());
        command.add("-storepass");
        command.add(CERTIFICATE_PASSWORD);
        command.add("-keypass");
        command.add(CERTIFICATE_PASSWORD);
        command.add("-validity");
        command.add("365");
        command.add("-noprompt");

        Process process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .start();
        String outputText = new String(process.getInputStream().readAllBytes());
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException(
                "keytool falhou ao preparar certificado X.509 de teste: " + outputText
            );
        }
        return output;
    }

    private static FiscalEstablishment establishment() {
        Person person = new Person();
        person.setTipoPessoa(TipoPessoa.PJ);
        person.setName("EMPRESA TESTE");
        person.setCpfCnpj("12345678000195");

        LegalEntity legalEntity = new LegalEntity();
        legalEntity.setPerson(person);
        legalEntity.setNomeFantasia("SYSTEMPRO TESTE");
        legalEntity.setInscricaoEstadual("0623079040081");
        person.setLegalEntity(legalEntity);

        PersonAddress address = new PersonAddress();
        address.setType(AddressType.COMERCIAL);
        address.setCep("30140071");
        address.setLogradouro("AV AFONSO PENA");
        address.setNumero("1000");
        address.setBairro("CENTRO");
        address.setCidade("BELO HORIZONTE");
        address.setUf("MG");
        address.setPrincipal(true);
        person.addAddress(address);

        Company company = new Company(person);

        FiscalEstablishment establishment = new FiscalEstablishment(company);
        establishment.setMunicipalityIbgeCode("3106200");
        establishment.setTaxRegime(TaxRegime.REGIME_NORMAL);
        establishment.setEnvironment(FiscalEnvironment.HOMOLOGACAO);
        establishment.setSeries(1);
        return establishment;
    }

    private static SaleItem item(
        int number,
        String code,
        String gross,
        String discount,
        String total,
        String rtcBase,
        String ibsUf,
        String ibsMunicipal,
        String ibsTotal,
        String cbs
    ) {
        SaleItem item = new SaleItem();
        item.setItemNumber(number);
        item.setCodeSnapshot(code);
        item.setNameSnapshot("PRODUTO TESTE " + number);
        item.setUnitSnapshot("UN");
        item.setNcmSnapshot("61091000");
        item.setOriginSnapshot("0");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal(gross));
        item.setDiscount(new BigDecimal(discount));
        item.setTotal(new BigDecimal(total));
        item.setCfopSnapshot("5102");
        item.setIcmsCstCsosnSnapshot("00");
        item.setIcmsRate(new BigDecimal("18.0000"));
        item.setPisCstSnapshot("06");
        item.setCofinsCstSnapshot("06");
        item.setIbsCbsCalculation(new IbsCbsCalculation(
            "000",
            "000001",
            new BigDecimal(rtcBase),
            new IbsCbsJurisdictionCalculation(
                new BigDecimal("0.100000"),
                new BigDecimal(ibsUf)
            ),
            new IbsCbsJurisdictionCalculation(
                new BigDecimal("0.000000"),
                new BigDecimal(ibsMunicipal)
            ),
            new BigDecimal(ibsTotal),
            new IbsCbsJurisdictionCalculation(
                new BigDecimal("0.900000"),
                new BigDecimal(cbs)
            )
        ));
        return item;
    }

    private static void setId(AuditableEntity entity, Long id) throws Exception {
        Field field = AuditableEntity.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}