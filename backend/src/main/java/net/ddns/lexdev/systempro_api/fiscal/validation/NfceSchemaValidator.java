package net.ddns.lexdev.systempro_api.fiscal.validation;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import net.ddns.lexdev.systempro_api.config.NfceSchemaProperties;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;

/**
 * Valida a NFe modelo 65 contida no XML de autorização contra o schema oficial
 * de documento do pacote PL configurado.
 *
 * Os pacotes PL incrementais (como o PL_010f) publicam o nfe_v4.00.xsd e suas
 * dependências de leiaute. O enviNFe é envelope de transporte e não faz parte
 * desse pacote incremental; por isso a validação fiscal extrai a NFe do envelope
 * antes de aplicar o schema.
 */
@Service
public class NfceSchemaValidator {

    private final ResourceLoader resourceLoader;
    private final NfceSchemaProperties properties;

    public NfceSchemaValidator(ResourceLoader resourceLoader, NfceSchemaProperties properties) {
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }

    public void validate(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new FiscalIntegrationException("O XML da NFC-e não foi gerado para validação.");
        }

        Resource schemaResource = resourceLoader.getResource(properties.location());
        if (!schemaResource.exists()) {
            throw new FiscalIntegrationException(
                "O schema oficial da NFC-e não está disponível no backend: " + properties.location()
                    + ". Instale o pacote oficial de schemas correspondente ao leiaute configurado antes de habilitar a emissão fiscal."
            );
        }

        if (schemaResource.getFilename() == null || !schemaResource.getFilename().endsWith(".xsd")) {
            throw new FiscalIntegrationException(
                "A configuração do schema fiscal precisa apontar diretamente para um arquivo XSD: "
                    + properties.location()
            );
        }

        final Schema schema;
        try {
            schema = createSchema(schemaResource);
        } catch (Exception ex) {
            throw new FiscalIntegrationException(
                "Não foi possível carregar o pacote de schemas XSD da NFC-e configurado em "
                    + properties.location() + ".",
                ex
            );
        }

        try {
            Validator validator = schema.newValidator();
            ValidationErrorHandler errorHandler = new ValidationErrorHandler();
            validator.setErrorHandler(errorHandler);
            validator.validate(new StreamSource(new StringReader(extractNFe(xml))));

            if (!errorHandler.errors.isEmpty()) {
                throw new FiscalIntegrationException(buildValidationMessage(errorHandler.errors));
            }
        } catch (FiscalIntegrationException ex) {
            throw ex;
        } catch (SAXException ex) {
            throw new FiscalIntegrationException(
                "O XML da NFC-e não atende ao schema XSD oficial: " + ex.getMessage(),
                ex
            );
        } catch (Exception ex) {
            throw new FiscalIntegrationException(
                "Não foi possível validar o XML da NFC-e contra o schema XSD oficial.",
                ex
            );
        }
    }

    private String extractNFe(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        var document = factory.newDocumentBuilder()
            .parse(new org.xml.sax.InputSource(new StringReader(xml)));

        Element root = document.getDocumentElement();
        if ("NFe".equals(root.getLocalName())) {
            return xml;
        }

        NodeList nfeNodes = document.getElementsByTagNameNS(
            "http://www.portalfiscal.inf.br/nfe",
            "NFe"
        );
        if (nfeNodes.getLength() != 1) {
            throw new FiscalIntegrationException(
                "O XML fiscal precisa conter exatamente uma NFe para validação XSD."
            );
        }

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

        var transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(nfeNodes.item(0)), new StreamResult(writer));
        return writer.toString();
    }

    private Schema createSchema(Resource resource) throws Exception {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar");

        return factory.newSchema(new StreamSource(resource.getURL().toExternalForm()));
    }

    private static String buildValidationMessage(List<SAXParseException> errors) {
        StringBuilder message = new StringBuilder("O XML da NFC-e não atende ao schema XSD oficial:");
        for (SAXParseException error : errors) {
            message.append(' ')
                .append("linha ").append(error.getLineNumber())
                .append(", coluna ").append(error.getColumnNumber())
                .append(" - ").append(error.getMessage()).append(';');
        }
        return message.toString();
    }

    private static final class ValidationErrorHandler implements ErrorHandler {
        private final List<SAXParseException> errors = new ArrayList<>();

        @Override
        public void warning(SAXParseException exception) {
            // Avisos do XSD não invalidam o documento.
        }

        @Override
        public void error(SAXParseException exception) {
            errors.add(exception);
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            errors.add(exception);
            throw exception;
        }
    }
}