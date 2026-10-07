package net.ddns.lexdev.systempro_api.fiscal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;

import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Teste local da mecânica XMLDSig usada pelo emissor.
 *
 * Não representa um certificado A1 ICP-Brasil e não substitui a homologação
 * fiscal. A finalidade é provar, sem dependências externas, que a referência
 * por Id, o digest e a assinatura RSA do XML podem ser gerados e verificados.
 */
class XmlDigitalSignatureTest {

    @Test
    void shouldValidateGeneratedXmlSignature() throws Exception {
        KeyPair keyPair = generateKeyPair();
        Document document = document(
            "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<infNFe Id=\"NFe35123456789012345678901234567890123456789012\" versao=\"4.00\">"
                + "<ide/>"
                + "</infNFe>"
                + "</NFe>"
        );

        sign(document, keyPair);

        assertTrue(validate(document, keyPair));
    }

    @Test
    void shouldRejectSignatureWhenSignedContentIsChanged() throws Exception {
        KeyPair keyPair = generateKeyPair();
        Document document = document(
            "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<infNFe Id=\"NFe35123456789012345678901234567890123456789012\" versao=\"4.00\">"
                + "<ide><cNF>12345678</cNF></ide>"
                + "</infNFe>"
                + "</NFe>"
        );

        sign(document, keyPair);

        Element cNF = (Element) document.getElementsByTagNameNS("*", "cNF").item(0);
        cNF.setTextContent("87654321");

        assertFalse(validate(document, keyPair));
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static Document document(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
        );
    }

    private static void sign(Document document, KeyPair keyPair) throws Exception {
        Element target = (Element) document.getElementsByTagNameNS("*", "infNFe").item(0);

        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
        Reference reference = factory.newReference(
            "#" + target.getAttribute("Id"),
            factory.newDigestMethod(DigestMethod.SHA1, null),
            List.of(factory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)),
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

        DOMSignContext context = new DOMSignContext(keyPair.getPrivate(), target.getParentNode());
        context.setDefaultNamespacePrefix("ds");
        context.setIdAttributeNS(target, null, "Id");

        factory.newXMLSignature(signedInfo, null).sign(context);
    }

    private static boolean validate(Document document, KeyPair keyPair) throws Exception {
        Element target = (Element) document.getElementsByTagNameNS("*", "infNFe").item(0);
        Element signature = (Element) document.getElementsByTagNameNS(
            XMLSignature.XMLNS,
            "Signature"
        ).item(0);

        DOMValidateContext context = new DOMValidateContext(keyPair.getPublic(), signature);
        context.setProperty("org.jcp.xml.dsig.secureValidation",Boolean.FALSE);
        context.setIdAttributeNS(target, null, "Id");

        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
        XMLSignature xmlSignature = factory.unmarshalXMLSignature(context);
        return xmlSignature.validate(context);
    }
}
