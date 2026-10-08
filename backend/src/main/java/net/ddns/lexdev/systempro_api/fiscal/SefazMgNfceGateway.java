package net.ddns.lexdev.systempro_api.fiscal;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import net.ddns.lexdev.systempro_api.config.FiscalProperties;
import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.PersonAddress;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.domain.SalePayment;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.FiscalEnvironment;
import net.ddns.lexdev.systempro_api.enums.PaymentMethod;
import net.ddns.lexdev.systempro_api.exception.FiscalCommunicationException;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;
import net.ddns.lexdev.systempro_api.service.FiscalEstablishmentService;
import net.ddns.lexdev.systempro_api.storage.FileStorageService;
import net.ddns.lexdev.systempro_api.fiscal.validation.NfceSchemaValidator;

@Service
public class SefazMgNfceGateway implements SefazNfceGateway {

    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final String SOAP_NS = "http://www.w3.org/2003/05/soap-envelope";
    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter XML_DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final FiscalEstablishmentService establishmentService;
    private final FileStorageService storage;
    private final FiscalProperties properties;
    private final NfceSchemaValidator schemaValidator;
    private final NfceQrCodeGenerator qrCodeGenerator;
    private final SecureRandom random = new SecureRandom();

    public SefazMgNfceGateway(
        FiscalEstablishmentService establishmentService,
        FileStorageService storage,
        FiscalProperties properties,
        NfceSchemaValidator schemaValidator,
        NfceQrCodeGenerator qrCodeGenerator
    ) {
        this.establishmentService = establishmentService;
        this.storage = storage;
        this.properties = properties;
        this.schemaValidator = schemaValidator;
        this.qrCodeGenerator = qrCodeGenerator;
    }

    @Override
    public NfceIssueResult authorize(
        FiscalEstablishment establishment,
        Sale sale,
        FiscalDocument document
    ) {
        ZonedDateTime emission = ZonedDateTime.now(BRAZIL_ZONE);
        String cNF = randomDigits(8);
        int tpEmis = 1;
        String accessKey = buildAccessKey(
            establishment.getCompany().getPerson().getCpfCnpj(),
            emission,
            document.getSeries(),
            document.getNumber(),
            tpEmis,
            cNF
        );

        String xml = NfceXmlBuilder.build(
            establishment,
            sale,
            document,
            accessKey,
            cNF,
            tpEmis,
            XML_DATE_TIME.format(emission),
            properties.schemaVersion(),
            properties.productVersion(),
            qrCodeGenerator.onlineUrl(accessKey, establishment.getEnvironment()),
            qrCodeGenerator.consultationUrl(establishment.getEnvironment())
        );
        String signedXml = sign(xml, establishment);
        schemaValidator.validate(signedXml);
        document.setAccessKey(accessKey);
        document.setXml(signedXml);

        SoapResponse soap;
        try {
            soap = post(
                endpoint(establishment, "NFeAutorizacao4"),
                action("NFeAutorizacao4", "nfeAutorizacaoLote"),
                wrap("NFeAutorizacao4", "enviNFe", signedXml),
                establishment
            );
        } catch (FiscalCommunicationException ex) {
            return pendingAuthorization(accessKey, signedXml, null, ex.getMessage());
        }

        ProtocolResult protocol;
        String statusCode;
        String reason;
        String receipt;
        try {
            protocol = extractProtocol(soap.xml());
            statusCode = protocol.code() == null ? firstValue(soap.xml(), "cStat") : protocol.code();
            reason = firstNonBlank(
                protocol.reason(),
                firstValue(soap.xml(), "xMotivo")
            );
            receipt = firstValue(soap.xml(), "nRec");
        } catch (RuntimeException ex) {
            return pendingAuthorization(
                accessKey,
                signedXml,
                soap.xml(),
                "A SEFAZ/MG respondeu, mas não foi possível interpretar o retorno com segurança: " + safeMessage(ex)
            );
        }

        if ("103".equals(statusCode)) {
            return new NfceIssueResult(
                FiscalDocumentStatus.PENDENTE_CONSULTA,
                accessKey,
                signedXml,
                soap.xml(),
                null,
                receipt,
                reason,
                null
            );
        }
        if ("100".equals(statusCode) || "150".equals(statusCode)) {
            return new NfceIssueResult(
                FiscalDocumentStatus.AUTORIZADA,
                firstNonBlank(protocol.accessKey(), accessKey),
                signedXml,
                soap.xml(),
                protocol.protocol(),
                null,
                reason,
                Instant.now()
            );
        }

        return new NfceIssueResult(
            FiscalDocumentStatus.REJEITADA,
            accessKey,
            signedXml,
            soap.xml(),
            protocol.protocol(),
            null,
            reason,
            null
        );
    }

    private static NfceIssueResult pendingAuthorization(
        String accessKey,
        String signedXml,
        String responseXml,
        String reason
    ) {
        return new NfceIssueResult(
            FiscalDocumentStatus.PENDENTE_CONSULTA,
            accessKey,
            signedXml,
            responseXml,
            null,
            null,
            reason,
            null
        );
    }

    @Override
    public NfceIssueResult consult(
        FiscalEstablishment establishment,
        FiscalDocument document
    ) {
        if (document.getAccessKey() == null || document.getAccessKey().length() != 44) {
            throw new FiscalIntegrationException("A chave de acesso da NFC-e não está disponível para consulta.");
        }

        SoapResponse soap;
        try {
            if (document.getReceiptNumber() != null && !document.getReceiptNumber().isBlank()) {
                String body = "<consReciNFe xmlns=\"" + NFE_NS + "\" versao=\"4.00\">"
                    + "<tpAmb>" + environmentCode(establishment.getEnvironment()) + "</tpAmb>"
                    + "<nRec>" + esc(document.getReceiptNumber()) + "</nRec>"
                    + "</consReciNFe>";
                soap = post(
                    endpoint(establishment, "NFeRetAutorizacao4"),
                    action("NFeRetAutorizacao4", "nfeRetAutorizacaoLote"),
                    wrap("NFeRetAutorizacao4", "nfeDadosMsg", body),
                    establishment
                );
            } else {
                String body = "<consSitNFe xmlns=\"" + NFE_NS + "\" versao=\"4.00\">"
                    + "<tpAmb>" + environmentCode(establishment.getEnvironment()) + "</tpAmb>"
                    + "<xServ>CONSULTAR</xServ>"
                    + "<chNFe>" + esc(document.getAccessKey()) + "</chNFe>"
                    + "</consSitNFe>";
                soap = post(
                    endpoint(establishment, "NFeConsultaProtocolo4"),
                    action("NFeConsultaProtocolo4", "nfeConsultaNF"),
                    wrap("NFeConsultaProtocolo4", "nfeDadosMsg", body),
                    establishment
                );
            }
        } catch (FiscalCommunicationException ex) {
            return pendingConsultation(document, null, ex.getMessage());
        }

        final ProtocolResult protocol;
        final String statusCode;
        final String reason;
        try {
            protocol = extractProtocol(soap.xml());
            statusCode = protocol.code() == null ? firstValue(soap.xml(), "cStat") : protocol.code();
            reason = firstNonBlank(protocol.reason(), firstValue(soap.xml(), "xMotivo"));
        } catch (RuntimeException ex) {
            return pendingConsultation(
                document,
                soap.xml(),
                "A SEFAZ/MG respondeu à consulta, mas não foi possível interpretar o retorno com segurança: "
                    + safeMessage(ex)
            );
        }

        if ("100".equals(statusCode) || "150".equals(statusCode)) {
            return new NfceIssueResult(
                FiscalDocumentStatus.AUTORIZADA,
                firstNonBlank(protocol.accessKey(), document.getAccessKey()),
                document.getXml(),
                soap.xml(),
                protocol.protocol(),
                null,
                reason,
                Instant.now()
            );
        }
        if ("110".equals(statusCode) || "301".equals(statusCode) || "302".equals(statusCode)) {
            return new NfceIssueResult(
                FiscalDocumentStatus.REJEITADA,
                document.getAccessKey(),
                document.getXml(),
                soap.xml(),
                protocol.protocol(),
                null,
                reason,
                null
            );
        }

        return new NfceIssueResult(
            FiscalDocumentStatus.PENDENTE_CONSULTA,
            document.getAccessKey(),
            document.getXml(),
            soap.xml(),
            protocol.protocol(),
            document.getReceiptNumber(),
            reason,
            null
        );
    }

    private static NfceIssueResult pendingConsultation(
        FiscalDocument document,
        String responseXml,
        String reason
    ) {
        return new NfceIssueResult(
            FiscalDocumentStatus.PENDENTE_CONSULTA,
            document.getAccessKey(),
            document.getXml(),
            responseXml,
            null,
            document.getReceiptNumber(),
            reason,
            null
        );
    }

    @Override
    public NfceIssueResult cancel(
        FiscalEstablishment establishment,
        FiscalDocument document,
        String justification
    ) {
        if (document.getAccessKey() == null || document.getAccessKey().length() != 44) {
            throw new FiscalIntegrationException("A chave de acesso da NFC-e não está disponível para cancelamento.");
        }
        if (document.getProtocol() == null || document.getProtocol().isBlank()) {
            throw new FiscalIntegrationException("O protocolo de autorização da NFC-e não está disponível para cancelamento.");
        }

        String now = XML_DATE_TIME.format(ZonedDateTime.now(BRAZIL_ZONE));
        String eventId = "ID110111" + document.getAccessKey() + "01";
        String body = "<envEvento xmlns=\"" + NFE_NS + "\" versao=\"1.00\">"
            + "<idLote>" + randomDigits(15) + "</idLote>"
            + "<evento versao=\"1.00\">"
            + "<infEvento Id=\"" + eventId + "\">"
            + "<cOrgao>31</cOrgao>"
            + "<tpAmb>" + environmentCode(establishment.getEnvironment()) + "</tpAmb>"
            + "<CNPJ>" + esc(establishment.getCompany().getPerson().getCpfCnpj()) + "</CNPJ>"
            + "<chNFe>" + esc(document.getAccessKey()) + "</chNFe>"
            + "<dhEvento>" + esc(now) + "</dhEvento>"
            + "<tpEvento>110111</tpEvento>"
            + "<nSeqEvento>1</nSeqEvento>"
            + "<verEvento>1.00</verEvento>"
            + "<detEvento versao=\"1.00\">"
            + "<descEvento>Cancelamento</descEvento>"
            + "<nProt>" + esc(document.getProtocol()) + "</nProt>"
            + "<xJust>" + esc(justification) + "</xJust>"
            + "</detEvento>"
            + "</infEvento>"
            + "</evento>"
            + "</envEvento>";

        String signedXml = sign(body, establishment);
        SoapResponse soap;
        try {
            soap = post(
                endpoint(establishment, "NFeRecepcaoEvento4"),
                action("NFeRecepcaoEvento4", "nfeRecepcaoEvento"),
                wrap("NFeRecepcaoEvento4", "nfeDadosMsg", signedXml),
                establishment
            );
        } catch (FiscalCommunicationException ex) {
            return pendingCancellation(document, signedXml, null, ex.getMessage());
        }

        final ProtocolResult protocol;
        final String statusCode;
        final String reason;
        try {
            protocol = extractEventProtocol(soap.xml());
            statusCode = protocol.code() == null ? firstValue(soap.xml(), "cStat") : protocol.code();
            reason = firstNonBlank(protocol.reason(), firstValue(soap.xml(), "xMotivo"));
        } catch (RuntimeException ex) {
            return pendingCancellation(
                document,
                signedXml,
                soap.xml(),
                "A SEFAZ/MG respondeu ao cancelamento, mas não foi possível interpretar o retorno com segurança: "
                    + safeMessage(ex)
            );
        }
        boolean cancelled = "135".equals(statusCode)
            || "136".equals(statusCode)
            || "155".equals(statusCode);

        return new NfceIssueResult(
            cancelled ? FiscalDocumentStatus.CANCELADA : FiscalDocumentStatus.CANCELAMENTO_PENDENTE,
            document.getAccessKey(),
            signedXml,
            soap.xml(),
            protocol.protocol(),
            null,
            reason,
            null
        );
    }

    private NfceIssueResult pendingCancellation(
        FiscalDocument document,
        String signedXml,
        String responseXml,
        String reason
    ) {
        return new NfceIssueResult(
            FiscalDocumentStatus.CANCELAMENTO_PENDENTE,
            document.getAccessKey(),
            signedXml,
            responseXml,
            null,
            null,
            reason,
            null
        );
    }

    @Override
    public NfceIssueResult status(FiscalEstablishment establishment) {
        String body = "<consStatServ xmlns=\"" + NFE_NS + "\" versao=\"4.00\">"
            + "<tpAmb>" + environmentCode(establishment.getEnvironment()) + "</tpAmb>"
            + "<cUF>31</cUF>"
            + "<xServ>STATUS</xServ>"
            + "</consStatServ>";
        SoapResponse soap = post(
            endpoint(establishment, "NFeStatusServico4"),
            action("NFeStatusServico4", "nfeStatusServicoNF"),
            wrap("NFeStatusServico4", "nfeDadosMsg", body),
            establishment
        );
        String code = firstValue(soap.xml(), "cStat");
        String reason = firstValue(soap.xml(), "xMotivo");
        return new NfceIssueResult(
            FiscalDocumentStatus.PENDENTE_CONSULTA,
            null,
            null,
            soap.xml(),
            null,
            null,
            reason,
            null
        );
    }

    private SoapResponse post(
        String endpoint,
        String action,
        String message,
        FiscalEstablishment establishment
    ) {
        final SSLContext sslContext;
        try {
            sslContext = sslContext(establishment);
        } catch (Exception ex) {
            throw new FiscalIntegrationException(
                "Não foi possível preparar o certificado A1/TLS para comunicação fiscal.",
                ex
            );
        }

        HttpClient client = HttpClient.newBuilder()
            .sslContext(sslContext)
            .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()))
            .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds()))
            .header("Content-Type", "application/soap+xml; charset=utf-8; action=\"" + action + "\"")
            .POST(HttpRequest.BodyPublishers.ofString(message, StandardCharsets.UTF_8))
            .build();

        final HttpResponse<String> response;
        try {
            response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FiscalCommunicationException(
                "Comunicação com a SEFAZ/MG foi interrompida; o resultado da transmissão é indeterminado.",
                ex
            );
        } catch (IOException ex) {
            throw new FiscalCommunicationException(
                "Não foi possível concluir a comunicação com a SEFAZ/MG; o resultado da transmissão é indeterminado.",
                ex
            );
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new FiscalCommunicationException(
                "SEFAZ/MG retornou HTTP " + response.statusCode()
                    + "; o resultado fiscal da transmissão deve ser confirmado por consulta."
            );
        }
        return new SoapResponse(response.body());
    }

    private SSLContext sslContext(FiscalEstablishment establishment) throws Exception {
        Resource resource = storage.download(establishment.getCertificateStorageKey());
        Path temp = Files.createTempFile("systempro-fiscal-certificate-", ".p12");
        try {
            try (InputStream in = resource.getInputStream()) {
                Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            String password = establishmentService.decryptCertificatePassword(establishment);
            KeyStore certificateStore = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(temp)) {
                certificateStore.load(in, password.toCharArray());
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(certificateStore, password.toCharArray());

            javax.net.ssl.TrustManager[] trustManagers = null;
            if (properties.trustStorePath() != null && !properties.trustStorePath().isBlank()) {
                KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                try (InputStream in = Files.newInputStream(Path.of(properties.trustStorePath()))) {
                    trustStore.load(
                        in,
                        properties.trustStorePassword() == null
                            ? null
                            : properties.trustStorePassword().toCharArray()
                    );
                }
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
                trustManagers = tmf.getTrustManagers();
            }

            SSLContext context = SSLContext.getInstance("TLSv1.2");
            context.init(kmf.getKeyManagers(), trustManagers, random);
            return context;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String wrap(String service, String element, String payload) {
        return "<soap:Envelope xmlns:soap=\"" + SOAP_NS + "\">"
            + "<soap:Body><" + element + " xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/" + service + "\">"
            + stripXmlDeclaration(payload)
            + "</" + element + "></soap:Body></soap:Envelope>";
    }

    private static String action(String service, String operation) {
        return "http://www.portalfiscal.inf.br/nfe/wsdl/" + service + "/" + operation;
    }

    private String endpoint(FiscalEstablishment establishment, String service) {
        boolean prod = establishment.getEnvironment() == FiscalEnvironment.PRODUCAO;
        return switch (service) {
            case "NFeAutorizacao4" -> prod
                ? "https://nfce.fazenda.mg.gov.br/nfce/services/NFeAutorizacao4"
                : "https://hnfce.fazenda.mg.gov.br/nfce/services/NFeAutorizacao4";
            case "NFeRetAutorizacao4" -> prod
                ? "https://nfce.fazenda.mg.gov.br/nfce/services/NFeRetAutorizacao4"
                : "https://hnfce.fazenda.mg.gov.br/nfce/services/NFeRetAutorizacao4";
            case "NFeConsultaProtocolo4" -> prod
                ? "https://nfce.fazenda.mg.gov.br/nfce/services/NFeConsultaProtocolo4"
                : "https://hnfce.fazenda.mg.gov.br/nfce/services/NFeConsultaProtocolo4";
            case "NFeRecepcaoEvento4" -> prod
                ? "https://nfce.fazenda.mg.gov.br/nfce/services/NFeRecepcaoEvento4"
                : "https://hnfce.fazenda.mg.gov.br/nfce/services/NFeRecepcaoEvento4";
            case "NFeStatusServico4" -> prod
                ? "https://nfce.fazenda.mg.gov.br/nfce/services/NFeStatusServico4"
                : "https://hnfce.fazenda.mg.gov.br/nfce/services/NFeStatusServico4";
            default -> throw new FiscalIntegrationException("Web Service fiscal não suportado: " + service);
        };
    }

    private static int environmentCode(FiscalEnvironment environment) {
        return environment == FiscalEnvironment.PRODUCAO ? 1 : 2;
    }

    private static String buildAccessKey(
        String cnpj,
        ZonedDateTime emission,
        int series,
        long number,
        int tpEmis,
        String cNF
    ) {
        String base = "31"
            + String.format("%02d%02d", emission.getYear() % 100, emission.getMonthValue())
            + cnpj
            + "65"
            + String.format("%03d", series)
            + String.format("%09d", number)
            + tpEmis
            + cNF;

        int sum = 0;
        int weight = 2;
        for (int i = base.length() - 1; i >= 0; i--) {
            sum += Character.digit(base.charAt(i), 10) * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int digit = 11 - (sum % 11);
        if (digit >= 10) {
            digit = 0;
        }
        return base + digit;
    }

    private String sign(String xml, FiscalEstablishment establishment) {
        Path temp = null;
        try {
            Resource resource = storage.download(establishment.getCertificateStorageKey());
            temp = Files.createTempFile("systempro-fiscal-sign-", ".p12");
            try (InputStream in = resource.getInputStream()) {
                Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            String password = establishmentService.decryptCertificatePassword(establishment);
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(temp)) {
                keyStore.load(in, password.toCharArray());
            }

            String alias = keyStore.aliases().nextElement();
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
            if (privateKey == null || certificate == null) {
                throw new FiscalIntegrationException("O certificado A1 não possui chave privada utilizável.");
            }

            Document document = parse(xml);
            Element target = (Element) firstElement(document, "infNFe");
            if (target == null) {
                target = (Element) firstElement(document, "infEvento");
            }
            if (target == null || !target.hasAttribute("Id")) {
                throw new FiscalIntegrationException("O XML fiscal não contém o elemento de assinatura esperado.");
            }

            XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
            List<Transform> transforms = List.of(
                factory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                factory.newTransform(
                    javax.xml.crypto.dsig.CanonicalizationMethod.INCLUSIVE,
                    (TransformParameterSpec) null
                )
            );
            Reference reference = factory.newReference(
                "#" + target.getAttribute("Id"),
                factory.newDigestMethod(DigestMethod.SHA1, null),
                transforms,
                null,
                null
            );
            SignedInfo signedInfo = factory.newSignedInfo(
                factory.newCanonicalizationMethod(
                    javax.xml.crypto.dsig.CanonicalizationMethod.INCLUSIVE,
                    (C14NMethodParameterSpec) null
                ),
                factory.newSignatureMethod(SignatureMethod.RSA_SHA1, null),
                List.of(reference)
            );

            KeyInfoFactory kif = factory.getKeyInfoFactory();
            X509Data x509Data = kif.newX509Data(List.of(certificate));
            KeyInfo keyInfo = kif.newKeyInfo(List.of(x509Data));
            var context = new javax.xml.crypto.dsig.dom.DOMSignContext(
                privateKey,
                target.getParentNode()
            );
            context.setDefaultNamespacePrefix("ds");
            context.setIdAttributeNS(target, null, "Id");

            XMLSignature signature = factory.newXMLSignature(signedInfo, keyInfo);
            signature.sign(context);

            validateGeneratedSignature(document, certificate.getPublicKey());
            return serialize(document);
        } catch (FiscalIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new FiscalIntegrationException(
                "Não foi possível assinar o XML fiscal com o certificado A1.",
                ex
            );
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (Exception ignored) {
                    // não propaga falha de limpeza do arquivo temporário
                }
            }
        }
    }


    /**
     * Verifica criptograficamente a assinatura XML recém-gerada antes que o
     * documento fiscal possa seguir para transmissão.
     *
     * A validação utiliza a chave pública do mesmo certificado que assinou o
     * documento e também valida o digest da referência ao infNFe/infEvento.
     * Assim, qualquer alteração no XML após a assinatura ou qualquer assinatura
     * estruturalmente inconsistente é detectada localmente.
     */
    private void validateGeneratedSignature(Document document, PublicKey publicKey) {
        try {
            Element signatureElement = (Element) firstElement(document, "Signature");
            if (signatureElement == null) {
                throw new FiscalIntegrationException(
                    "O XML fiscal não contém a assinatura digital após o processo de assinatura."
                );
            }

            Element target = (Element) firstElement(document, "infNFe");
            if (target == null) {
                target = (Element) firstElement(document, "infEvento");
            }
            if (target == null || !target.hasAttribute("Id")) {
                throw new FiscalIntegrationException(
                    "O XML fiscal não contém o elemento referenciado pela assinatura digital."
                );
            }

            DOMValidateContext validateContext = new DOMValidateContext(publicKey, signatureElement);
            validateContext.setProperty("org.jcp.xml.dsig.secureValidation",Boolean.FALSE);
            validateContext.setIdAttributeNS(target, null, "Id");

            XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
            XMLSignature generatedSignature = factory.unmarshalXMLSignature(validateContext);

            if (!generatedSignature.validate(validateContext)) {
                boolean signatureValueValid =
                    generatedSignature.getSignatureValue().validate(validateContext);

                boolean referencesValid = true;
                for (Object referenceObject : generatedSignature.getSignedInfo().getReferences()) {
                    Reference signedReference = (Reference) referenceObject;
                    if (!signedReference.validate(validateContext)) {
                        referencesValid = false;
                        break;
                    }
                }

                String detail;
                if (!signatureValueValid) {
                    detail = "SignatureValue inválido";
                } else if (!referencesValid) {
                    detail = "Digest/Reference inválido";
                } else {
                    detail = "assinatura XMLDSig inválida";
                }

                throw new FiscalIntegrationException(
                    "A assinatura digital gerada para o XML fiscal não pôde ser validada: " + detail + "."
                );
            }
        } catch (FiscalIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new FiscalIntegrationException(
                "Não foi possível validar criptograficamente a assinatura digital gerada para o XML fiscal.",
                ex
            );
        }
    }

    static final class NfceXmlBuilder {

        private NfceXmlBuilder() {}

        static String build(
            FiscalEstablishment establishment,
            Sale sale,
            FiscalDocument document,
            String accessKey,
            String cNF,
            int tpEmis,
            String emissionDateTime,
            String schemaVersion,
            String productVersion,
            String qrCodeUrl,
            String consultationUrl
        ) {
            PersonAddress address = establishment.getCompany().getPerson().getAddresses().stream()
                .filter(PersonAddress::isPrincipal)
                .findFirst()
                .orElseGet(() -> establishment.getCompany().getPerson().getAddresses().stream().findFirst().orElse(null));
            validateAddress(address);

            String issuerName = esc(establishment.getCompany().getPerson().getName());
            String tradeName = establishment.getCompany().getPerson().getLegalEntity() == null
                ? null
                : establishment.getCompany().getPerson().getLegalEntity().getNomeFantasia();
            String stateRegistration = establishment.getCompany().getPerson().getLegalEntity() == null
                ? null
                : establishment.getCompany().getPerson().getLegalEntity().getInscricaoEstadual();

            if (stateRegistration == null || stateRegistration.isBlank()) {
                throw new FiscalIntegrationException("A Inscrição Estadual do emitente é obrigatória para a emissão configurada.");
            }

            StringBuilder xml = new StringBuilder(16_000);
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<enviNFe xmlns=\"").append(NFE_NS).append("\" versao=\"").append(esc(schemaVersion)).append("\">")
                .append("<idLote>").append(document.getId()).append("</idLote>")
                .append("<indSinc>1</indSinc>")
                .append("<NFe><infNFe Id=\"NFe").append(accessKey).append("\" versao=\"").append(esc(schemaVersion)).append("\">");

            xml.append("<ide>")
                .append("<cUF>31</cUF>")
                .append("<cNF>").append(cNF).append("</cNF>")
                .append("<natOp>VENDA</natOp>")
                .append("<mod>65</mod>")
                .append("<serie>").append(document.getSeries()).append("</serie>")
                .append("<nNF>").append(document.getNumber()).append("</nNF>")
                .append("<dhEmi>").append(emissionDateTime).append("</dhEmi>")
                .append("<tpNF>1</tpNF>")
                .append("<idDest>1</idDest>")
                .append("<cMunFG>").append(esc(establishment.getMunicipalityIbgeCode())).append("</cMunFG>")
                .append("<tpImp>4</tpImp>")
                .append("<tpEmis>").append(tpEmis).append("</tpEmis>")
                .append("<cDV>").append(accessKey.charAt(accessKey.length() - 1)).append("</cDV>")
                .append("<tpAmb>").append(environmentCode(establishment.getEnvironment())).append("</tpAmb>")
                .append("<finNFe>1</finNFe>")
                .append("<indFinal>1</indFinal>")
                .append("<indPres>1</indPres>")
                .append("<procEmi>0</procEmi>")
                .append("<verProc>").append(esc(productVersion)).append("</verProc>")
                .append("</ide>");

            xml.append("<emit>")
                .append("<CNPJ>").append(esc(establishment.getCompany().getPerson().getCpfCnpj())).append("</CNPJ>")
                .append("<xNome>").append(issuerName).append("</xNome>");
            if (tradeName != null && !tradeName.isBlank()) {
                xml.append("<xFant>").append(esc(tradeName)).append("</xFant>");
            }
            xml.append("<enderEmit>")
                .append("<xLgr>").append(esc(address.getLogradouro())).append("</xLgr>")
                .append("<nro>").append(esc(address.getNumero())).append("</nro>");
            if (address.getComplemento() != null && !address.getComplemento().isBlank()) {
                xml.append("<xCpl>").append(esc(address.getComplemento())).append("</xCpl>");
            }
            xml.append("<xBairro>").append(esc(address.getBairro())).append("</xBairro>")
                .append("<cMun>").append(esc(establishment.getMunicipalityIbgeCode())).append("</cMun>")
                .append("<xMun>").append(esc(address.getCidade())).append("</xMun>")
                .append("<UF>").append(esc(address.getUf())).append("</UF>")
                .append("<CEP>").append(esc(address.getCep())).append("</CEP>")
                .append("<cPais>1058</cPais><xPais>BRASIL</xPais>")
                .append("</enderEmit>")
                .append("<IE>").append(esc(stateRegistration)).append("</IE>")
                .append("<CRT>").append(establishment.getTaxRegime().sefazCrtCode()).append("</CRT>")
                .append("</emit>");

            String consumerDocument = sale.getConsumerCpfCnpj();
            if ((consumerDocument == null || consumerDocument.isBlank()) && sale.getClient() != null) {
                consumerDocument = sale.getClient().getPerson().getCpfCnpj();
            }
            if (consumerDocument != null && !consumerDocument.isBlank()) {
                xml.append("<dest>");
                if (consumerDocument.length() == 11) {
                    xml.append("<CPF>").append(esc(consumerDocument)).append("</CPF>");
                } else if (consumerDocument.length() == 14) {
                    xml.append("<CNPJ>").append(esc(consumerDocument)).append("</CNPJ>");
                }
                xml.append("<indIEDest>9</indIEDest></dest>");
            }

            BigDecimal vProd = BigDecimal.ZERO;
            BigDecimal vDesc = BigDecimal.ZERO;
            BigDecimal vBC = BigDecimal.ZERO;
            BigDecimal vIcms = BigDecimal.ZERO;
            BigDecimal vPis = BigDecimal.ZERO;
            BigDecimal vCofins = BigDecimal.ZERO;

            for (SaleItem item : sale.getItems()) {
                BigDecimal gross = item.getTotal().add(item.getDiscount());
                xml.append("<det nItem=\"").append(item.getItemNumber()).append("\"><prod>")
                    .append("<cProd>").append(esc(item.getCodeSnapshot())).append("</cProd>")
                    .append("<cEAN>").append(gtinOrSemGtin(item.getGtinSnapshot())).append("</cEAN>")
                    .append("<xProd>").append(esc(item.getNameSnapshot())).append("</xProd>")
                    .append("<NCM>").append(esc(item.getNcmSnapshot())).append("</NCM>");
                if (item.getCestSnapshot() != null && !item.getCestSnapshot().isBlank()) {
                    xml.append("<CEST>").append(esc(item.getCestSnapshot())).append("</CEST>");
                }
                xml.append("<CFOP>").append(esc(item.getCfopSnapshot())).append("</CFOP>")
                    .append("<uCom>").append(esc(item.getUnitSnapshot())).append("</uCom>")
                    .append("<qCom>").append(fmt(item.getQuantity(), 4)).append("</qCom>")
                    .append("<vUnCom>").append(fmt(item.getUnitPrice(), 10)).append("</vUnCom>")
                    .append("<vProd>").append(fmt(gross, 2)).append("</vProd>")
                    .append("<cEANTrib>").append(gtinOrSemGtin(item.getGtinSnapshot())).append("</cEANTrib>")
                    .append("<uTrib>").append(esc(item.getUnitSnapshot())).append("</uTrib>")
                    .append("<qTrib>").append(fmt(item.getQuantity(), 4)).append("</qTrib>")
                    .append("<vUnTrib>").append(fmt(item.getUnitPrice(), 10)).append("</vUnTrib>");

                    if (item.getDiscount().signum() > 0) {
                        xml.append("<vDesc>").append(fmt(item.getDiscount(), 2)).append("</vDesc>");
                    }

                    xml.append("<indTot>1</indTot></prod><imposto>");

                IcmsTax icms = icms(item, establishment.getTaxRegime());
                xml.append(icms.xml());
                vBC = vBC.add(icms.base());
                vIcms = vIcms.add(icms.value());

                BigDecimal pis = taxValue(item.getPisCstSnapshot(), item.getPisRate(), item.getTotal(), item.getCodeSnapshot(), "PIS");
                BigDecimal cofins = taxValue(item.getCofinsCstSnapshot(), item.getCofinsRate(), item.getTotal(), item.getCodeSnapshot(), "COFINS");
                xml.append(pisXml(item, pis));
                xml.append(cofinsXml(item, cofins));
                xml.append(ibsCbsXml(item));
                vPis = vPis.add(pis);
                vCofins = vCofins.add(cofins);
                xml.append("</imposto></det>");

                vProd = vProd.add(gross);
                vDesc = vDesc.add(item.getDiscount());
            }

            BigDecimal totalPaid = sale.getPayments().stream()
                .map(SalePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal change = totalPaid.subtract(sale.getTotal()).max(BigDecimal.ZERO);

            xml.append("<total><ICMSTot>")
                .append("<vBC>").append(fmt(vBC, 2)).append("</vBC>")
                .append("<vICMS>").append(fmt(vIcms, 2)).append("</vICMS>")
                .append("<vICMSDeson>0.00</vICMSDeson><vFCP>0.00</vFCP>")
                .append("<vBCST>0.00</vBCST><vST>0.00</vST><vFCPST>0.00</vFCPST><vFCPSTRet>0.00</vFCPSTRet>")
                .append("<vProd>").append(fmt(vProd, 2)).append("</vProd>")
                .append("<vFrete>0.00</vFrete><vSeg>0.00</vSeg>")
                .append("<vDesc>").append(fmt(vDesc, 2)).append("</vDesc>")
                .append("<vII>0.00</vII><vIPI>0.00</vIPI><vIPIDevol>0.00</vIPIDevol>")
                .append("<vPIS>").append(fmt(vPis, 2)).append("</vPIS>")
                .append("<vCOFINS>").append(fmt(vCofins, 2)).append("</vCOFINS>")
                .append("<vOutro>0.00</vOutro>")
                .append("<vNF>").append(fmt(sale.getTotal(), 2)).append("</vNF>")
                .append("</ICMSTot>")
                .append(ibsCbsTotalXml(sale.getItems()))
                .append("</total>")
                .append("<transp><modFrete>9</modFrete></transp>")
                .append("<pag>");

            for (SalePayment payment : sale.getPayments()) {
                xml.append("<detPag>")
                    .append("<tPag>").append(payment.getPaymentMethod().getFiscalCode()).append("</tPag>")
                    .append("<vPag>").append(fmt(payment.getAmount(), 2)).append("</vPag>");
                appendCardDetails(xml, payment);
                xml.append("</detPag>");
            }
            if (change.signum() > 0) {
                xml.append("<vTroco>").append(fmt(change, 2)).append("</vTroco>");
            }
            xml.append("</pag>");

            if (sale.getNote() != null && !sale.getNote().isBlank()) {
                xml.append("<infAdic><infCpl>").append(esc(sale.getNote())).append("</infCpl></infAdic>");
            }
            xml.append("</infNFe>")
                .append("<infNFeSupl>")
                .append("<qrCode><![CDATA[").append(qrCodeUrl).append("]]></qrCode>")
                .append("<urlChave>").append(esc(consultationUrl)).append("</urlChave>")
                .append("</infNFeSupl>")
                .append("</NFe></enviNFe>");
            return xml.toString();
        }

        static String ibsCbsTotalXml(List<SaleItem> items) {
            BigDecimal taxBase = BigDecimal.ZERO;
            BigDecimal ibsUf = BigDecimal.ZERO;
            BigDecimal ibsMunicipal = BigDecimal.ZERO;
            BigDecimal ibs = BigDecimal.ZERO;
            BigDecimal cbs = BigDecimal.ZERO;
            boolean hasRtcSnapshot = false;

            for (SaleItem item : items) {
                var calculation = item.getIbsCbsCalculation();
                if (calculation == null) {
                    continue;
                }
                hasRtcSnapshot = true;
                taxBase = taxBase.add(calculation.taxBase());
                ibsUf = ibsUf.add(calculation.ibsUf().amount());
                ibsMunicipal = ibsMunicipal.add(calculation.ibsMunicipal().amount());
                ibs = ibs.add(calculation.totalIbs());
                cbs = cbs.add(calculation.cbs().amount());
            }

            if (!hasRtcSnapshot) {
                return "";
            }

            return "<IBSCBSTot>"
                + "<vBCIBSCBS>" + fmt(taxBase, 2) + "</vBCIBSCBS>"
                + "<gIBS>"
                + "<gIBSUF><vDif>0.00</vDif><vDevTrib>0.00</vDevTrib><vIBSUF>" + fmt(ibsUf, 2) + "</vIBSUF></gIBSUF>"
                + "<gIBSMun><vDif>0.00</vDif><vDevTrib>0.00</vDevTrib><vIBSMun>" + fmt(ibsMunicipal, 2) + "</vIBSMun></gIBSMun>"
                + "<vIBS>" + fmt(ibs, 2) + "</vIBS>"
                + "<vCredPres>0.00</vCredPres>"
                + "<vCredPresCondSus>0.00</vCredPresCondSus>"
                + "</gIBS>"
                + "<gCBS>"
                + "<vDif>0.00</vDif><vDevTrib>0.00</vDevTrib><vCBS>" + fmt(cbs, 2) + "</vCBS>"
                + "<vCredPres>0.00</vCredPres>"
                + "<vCredPresCondSus>0.00</vCredPresCondSus>"
                + "</gCBS>"
                + "</IBSCBSTot>";
        }

        static String ibsCbsXml(SaleItem item) {
            var calculation = item.getIbsCbsCalculation();
            if (calculation == null) {
                return "";
            }

            return "<IBSCBS>"
                + "<CST>" + esc(calculation.cst()) + "</CST>"
                + "<cClassTrib>" + esc(calculation.cClassTrib()) + "</cClassTrib>"
                + "<gIBSCBS>"
                + "<vBC>" + fmt(calculation.taxBase(), 2) + "</vBC>"
                + "<gIBSUF>"
                + "<pIBSUF>" + fmt(calculation.ibsUf().rate(), 4) + "</pIBSUF>"
                + "<vIBSUF>" + fmt(calculation.ibsUf().amount(), 2) + "</vIBSUF>"
                + "</gIBSUF>"
                + "<gIBSMun>"
                + "<pIBSMun>" + fmt(calculation.ibsMunicipal().rate(), 4) + "</pIBSMun>"
                + "<vIBSMun>" + fmt(calculation.ibsMunicipal().amount(), 2) + "</vIBSMun>"
                + "</gIBSMun>"
                + "<vIBS>" + fmt(calculation.totalIbs(), 2) + "</vIBS>"
                + "<gCBS>"
                + "<pCBS>" + fmt(calculation.cbs().rate(), 4) + "</pCBS>"
                + "<vCBS>" + fmt(calculation.cbs().amount(), 2) + "</vCBS>"
                + "</gCBS>"
                + "</gIBSCBS>"
                + "</IBSCBS>";
        }

        private static IcmsTax icms(SaleItem item, net.ddns.lexdev.systempro_api.enums.TaxRegime regime) {
            String code = item.getIcmsCstCsosnSnapshot();
            String origin = esc(item.getOriginSnapshot());

            if (regime.usesCsosn()) {
                return switch (code) {
                    case "102" -> new IcmsTax(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "<ICMS><ICMSSN102><orig>" + origin + "</orig><CSOSN>102</CSOSN></ICMSSN102></ICMS>"
                    );
                    case "103" -> new IcmsTax(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "<ICMS><ICMSSN103><orig>" + origin + "</orig><CSOSN>103</CSOSN></ICMSSN103></ICMS>"
                    );
                    case "300" -> new IcmsTax(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "<ICMS><ICMSSN300><orig>" + origin + "</orig><CSOSN>300</CSOSN></ICMSSN300></ICMS>"
                    );
                    case "400" -> new IcmsTax(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "<ICMS><ICMSSN400><orig>" + origin + "</orig><CSOSN>400</CSOSN></ICMSSN400></ICMS>"
                    );
                    default -> throw new FiscalIntegrationException(
                        "CSOSN " + code + " ainda não possui regra fiscal parametrizada no emissor."
                    );
                };
            }

            if ("00".equals(code)) {
                BigDecimal base = item.getTotal();
                BigDecimal rate = requireRate(item.getIcmsRate(), "ICMS", item.getCodeSnapshot());
                BigDecimal value = base.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                String xml = "<ICMS><ICMS00><orig>" + origin + "</orig><CST>00</CST>"
                    + "<modBC>3</modBC><vBC>" + fmt(base, 2) + "</vBC><pICMS>" + fmt(rate, 4)
                    + "</pICMS><vICMS>" + fmt(value, 2) + "</vICMS></ICMS00></ICMS>";
                return new IcmsTax(base, value, xml);
            }
            if ("40".equals(code) || "41".equals(code)) {
                return new IcmsTax(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    "<ICMS><ICMS40><orig>" + origin + "</orig><CST>" + code + "</CST></ICMS40></ICMS>"
                );
            }
            throw new FiscalIntegrationException(
                "CST ICMS " + code + " ainda não possui regra fiscal parametrizada no emissor."
            );
        }

        private static BigDecimal taxValue(
            String cst,
            BigDecimal rate,
            BigDecimal base,
            String productCode,
            String taxName
        ) {
            if (List.of("04", "05", "06", "07", "08", "09").contains(cst)) {
                return BigDecimal.ZERO;
            }
            if (List.of("01", "02").contains(cst)) {
                BigDecimal effectiveRate = requireRate(rate, taxName, productCode);
                return base.multiply(effectiveRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }
            throw new FiscalIntegrationException(
                "CST de " + taxName + " " + cst + " ainda não possui regra fiscal parametrizada no emissor."
            );
        }

        private static String pisXml(SaleItem item, BigDecimal ignored) {
            String cst = item.getPisCstSnapshot();
            if (List.of("04", "05", "06", "07", "08", "09").contains(cst)) {
                return "<PIS><PISNT><CST>" + esc(cst) + "</CST></PISNT></PIS>";
            }
            BigDecimal rate = requireRate(item.getPisRate(), "PIS", item.getCodeSnapshot());
            BigDecimal value = item.getTotal().multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return "<PIS><PISAliq><CST>" + esc(cst) + "</CST><vBC>" + fmt(item.getTotal(), 2)
                + "</vBC><pPIS>" + fmt(rate, 4) + "</pPIS><vPIS>" + fmt(value, 2)
                + "</vPIS></PISAliq></PIS>";
        }

        private static String cofinsXml(SaleItem item, BigDecimal ignored) {
            String cst = item.getCofinsCstSnapshot();
            if (List.of("04", "05", "06", "07", "08", "09").contains(cst)) {
                return "<COFINS><COFINSNT><CST>" + esc(cst) + "</CST></COFINSNT></COFINS>";
            }
            BigDecimal rate = requireRate(item.getCofinsRate(), "COFINS", item.getCodeSnapshot());
            BigDecimal value = item.getTotal().multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return "<COFINS><COFINSAliq><CST>" + esc(cst) + "</CST><vBC>" + fmt(item.getTotal(), 2)
                + "</vBC><pCOFINS>" + fmt(rate, 4) + "</pCOFINS><vCOFINS>" + fmt(value, 2)
                + "</vCOFINS></COFINSAliq></COFINS>";
        }

        private static BigDecimal requireRate(BigDecimal rate, String tax, String productCode) {
            if (rate == null) {
                throw new FiscalIntegrationException(
                    "O produto " + productCode + " não possui alíquota de " + tax + " parametrizada."
                );
            }
            return rate;
        }

        private static void appendCardDetails(StringBuilder xml, SalePayment payment) {
            if ((payment.getPaymentMethod() != PaymentMethod.CREDITO
                && payment.getPaymentMethod() != PaymentMethod.DEBITO)
                || (payment.getCardBrand() == null && payment.getAuthorizationCode() == null)) {
                return;
            }

            xml.append("<card><tpIntegra>2</tpIntegra>");
            if (payment.getCardBrand() != null && payment.getCardBrand().matches("\\d{2}")) {
                xml.append("<tBand>").append(esc(payment.getCardBrand())).append("</tBand>");
            }
            if (payment.getAuthorizationCode() != null && !payment.getAuthorizationCode().isBlank()) {
                xml.append("<cAut>").append(esc(payment.getAuthorizationCode())).append("</cAut>");
            }
            xml.append("</card>");
        }

        private static void validateAddress(PersonAddress address) {
            if (address == null) {
                throw new FiscalIntegrationException("O endereço do estabelecimento fiscal precisa estar cadastrado.");
            }
            if (isBlank(address.getLogradouro()) || isBlank(address.getNumero())
                || isBlank(address.getBairro()) || isBlank(address.getCidade())
                || isBlank(address.getUf()) || isBlank(address.getCep())) {
                throw new FiscalIntegrationException(
                    "O endereço principal do estabelecimento fiscal está incompleto para emissão."
                );
            }
        }

        private static String gtinOrSemGtin(String gtin) {
            return gtin == null || gtin.isBlank() ? "SEM GTIN" : esc(gtin);
        }

        private static BigDecimal zero() { return BigDecimal.ZERO.setScale(4); }

        private record IcmsTax(BigDecimal base, BigDecimal value, String xml) {}

        static String fmt(BigDecimal value, int scale) {
            return (value == null ? BigDecimal.ZERO : value)
                .setScale(scale, RoundingMode.HALF_UP)
                .toPlainString();
        }

        private static String esc(String value) { return SefazMgNfceGateway.esc(value); }
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    private static org.w3c.dom.Node firstElement(Document document, String localName) {
        return document.getElementsByTagNameNS("*", localName).item(0);
    }

    private static String firstValue(String xml, String localName) {
        try {
            Document document = parse(xml);
            var node = document.getElementsByTagNameNS("*", localName).item(0);
            return node == null ? null : node.getTextContent();
        } catch (Exception ex) {
            return null;
        }
    }

    private static ProtocolResult extractProtocol(String xml) {
        try {
            Document document = parse(xml);
            var infProt = document.getElementsByTagNameNS("*", "infProt").item(0);
            if (infProt != null) {
                String code = childValue(infProt, "cStat");
                String reason = childValue(infProt, "xMotivo");
                String protocol = childValue(infProt, "nProt");
                String accessKey = childValue(infProt, "chNFe");
                return new ProtocolResult(code, reason, protocol, accessKey);
            }
        } catch (Exception ignored) {
            // fallback para cStat/xMotivo da resposta externa
        }
        return new ProtocolResult(firstValue(xml, "cStat"), firstValue(xml, "xMotivo"), null, null);
    }

    private static ProtocolResult extractEventProtocol(String xml) {
        try {
            Document document = parse(xml);
            var infEvento = document.getElementsByTagNameNS("*", "infEvento").item(0);
            if (infEvento != null) {
                String code = childValue(infEvento, "cStat");
                String reason = childValue(infEvento, "xMotivo");
                String protocol = childValue(infEvento, "nProt");
                return new ProtocolResult(code, reason, protocol, null);
            }
        } catch (Exception ignored) {
            // fallback
        }
        return new ProtocolResult(firstValue(xml, "cStat"), firstValue(xml, "xMotivo"), firstValue(xml, "nProt"), null);
    }

    private static String childValue(org.w3c.dom.Node parent, String localName) {
        if (!(parent instanceof Element element)) {
            return null;
        }
        var nodes = element.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    private static String stripXmlDeclaration(String xml) {
        return xml.replaceFirst("^\\s*<\\?xml[^>]*\\?>", "");
    }

    private static String esc(String value) {
        if (value == null) return "";
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private static String randomDigits(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(ThreadLocalRandom.current().nextInt(10));
        }
        return value.toString();
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record SoapResponse(String xml) {}
    private record ProtocolResult(String code, String reason, String protocol, String accessKey) {}
}