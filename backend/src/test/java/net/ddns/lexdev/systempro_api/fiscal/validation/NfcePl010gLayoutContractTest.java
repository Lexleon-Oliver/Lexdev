package net.ddns.lexdev.systempro_api.fiscal.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Contrato estrutural dos XSDs do PL_010g recebidos para atualização fiscal.
 * A presença de campos opcionais no XSD não dispensa regras das Notas Técnicas.
 */
class NfcePl010gLayoutContractTest {
    private static final String ROOT = "fiscal/nfce/PL_010g/";
    private static final String XS = XMLConstants.W3C_XML_SCHEMA_NS_URI;

    @Test
    void deveConterOsCamposDoNovoLeiauteNasPosicoesOpcionais() throws Exception {
        URL url = getClass().getClassLoader().getResource(ROOT + "leiauteNFe_v4.00.xsd");
        assertNotNull(url, "Leiaute PL_010g não encontrado no classpath");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        Document document;
        try (InputStream in = url.openStream()) {
            document = factory.newDocumentBuilder().parse(in);
        }
        Map<String, Element> declarations = new HashMap<>();
        NodeList nodes = document.getElementsByTagNameNS(XS, "element");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            if (node.hasAttribute("name")) {
                declarations.put(node.getAttribute("name"), node);
            }
        }
        for (String name : new String[] {"vUnComLiq", "vProdLiq"}) {
            Element declaration = declarations.get(name);
            assertNotNull(declaration, "Campo ausente: " + name);
            Element sequence = (Element) declaration.getParentNode();
            assertEquals("sequence", sequence.getLocalName(), name);
            assertEquals("0", sequence.getAttribute("minOccurs"),
                "O grupo do valor líquido é opcional no XSD: " + name);
        }
        for (String name : new String[] {"vProdLiqTot", "gPgtoVinc"}) {
            Element declaration = declarations.get(name);
            assertNotNull(declaration, "Campo ausente: " + name);
            assertEquals("0", declaration.getAttribute("minOccurs"), name);
        }
    }

    @Test
    void deveCompilarOsDoisEventosIncluidosNoPacote() {
        for (String event : new String[] {"e110001_v1.00.xsd", "e110300_v1.00.xsd"}) {
            URL url = getClass().getClassLoader().getResource(ROOT + event);
            assertNotNull(url, "XSD de evento ausente: " + event);
            assertDoesNotThrow(() -> {
                SchemaFactory factory = SchemaFactory.newInstance(XS);
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar");
                factory.newSchema(url);
            }, "Falha ao compilar schema de evento " + event);
        }
    }
}
