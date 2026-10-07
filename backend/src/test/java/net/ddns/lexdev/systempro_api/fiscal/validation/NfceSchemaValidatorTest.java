package net.ddns.lexdev.systempro_api.fiscal.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import net.ddns.lexdev.systempro_api.config.NfceSchemaProperties;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;

class NfceSchemaValidatorTest {

    @Test
    void deveValidarXmlUsandoIncludesRelativos(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("common.xsd"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="urn:systempro:nfce-test"
                       xmlns="urn:systempro:nfce-test"
                       elementFormDefault="qualified">
              <xs:complexType name="TEnviNFe">
                <xs:sequence>
                  <xs:element name="idLote" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """);

        Files.writeString(tempDir.resolve("enviNFe_v4.00.xsd"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns="urn:systempro:nfce-test"
                       targetNamespace="urn:systempro:nfce-test"
                       elementFormDefault="qualified">
              <xs:include schemaLocation="common.xsd"/>
              <xs:element name="enviNFe" type="TEnviNFe"/>
            </xs:schema>
            """);

        NfceSchemaValidator validator = new NfceSchemaValidator(
            new DefaultResourceLoader(),
            new NfceSchemaProperties(tempDir.resolve("enviNFe_v4.00.xsd").toUri().toString())
        );

        String validXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <enviNFe xmlns="urn:systempro:nfce-test"><idLote>1</idLote></enviNFe>
            """;

        assertDoesNotThrow(() -> validator.validate(validXml));
    }

    @Test
    void deveRejeitarXmlQueNaoAtendeAoSchema(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("enviNFe_v4.00.xsd"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns="urn:systempro:nfce-test"
                       targetNamespace="urn:systempro:nfce-test"
                       elementFormDefault="qualified">
              <xs:element name="enviNFe">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="idLote" type="xs:string"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """);

        NfceSchemaValidator validator = new NfceSchemaValidator(
            new DefaultResourceLoader(),
            new NfceSchemaProperties(tempDir.resolve("enviNFe_v4.00.xsd").toUri().toString())
        );

        String invalidXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <enviNFe xmlns="urn:systempro:nfce-test"><campoInexistente>1</campoInexistente></enviNFe>
            """;

        FiscalIntegrationException ex = assertThrows(
            FiscalIntegrationException.class,
            () -> validator.validate(invalidXml)
        );
        assertTrue(ex.getMessage().contains("schema XSD oficial"));
    }
}
