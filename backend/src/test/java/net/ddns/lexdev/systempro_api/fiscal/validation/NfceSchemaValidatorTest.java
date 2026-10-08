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

    private static final String NFE_NAMESPACE = "http://www.portalfiscal.inf.br/nfe";

    @Test
    void deveValidarXmlUsandoIncludesRelativos(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("common.xsd"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="http://www.portalfiscal.inf.br/nfe"
                       xmlns="http://www.portalfiscal.inf.br/nfe"
                       elementFormDefault="qualified">
              <xs:complexType name="TNFe">
                <xs:sequence>
                  <xs:element name="infNFe" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """);

        Files.writeString(tempDir.resolve("nfe_v4.00.xsd"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns="http://www.portalfiscal.inf.br/nfe"
                       targetNamespace="http://www.portalfiscal.inf.br/nfe"
                       elementFormDefault="qualified">
              <xs:include schemaLocation="common.xsd"/>
              <xs:element name="NFe" type="TNFe"/>
            </xs:schema>
            """);

        NfceSchemaValidator validator = new NfceSchemaValidator(
            new DefaultResourceLoader(),
            new NfceSchemaProperties(tempDir.resolve("nfe_v4.00.xsd").toUri().toString())
        );

        String validXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <NFe xmlns="%s"><infNFe>conteudo-valido</infNFe></NFe>
            """.formatted(NFE_NAMESPACE);

        assertDoesNotThrow(() -> validator.validate(validXml));
    }

    @Test
    void deveRejeitarXmlQueNaoAtendeAoSchema(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("nfe_v4.00.xsd"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns="http://www.portalfiscal.inf.br/nfe"
                       targetNamespace="http://www.portalfiscal.inf.br/nfe"
                       elementFormDefault="qualified">
              <xs:element name="NFe">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="infNFe" type="xs:string"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """);

        NfceSchemaValidator validator = new NfceSchemaValidator(
            new DefaultResourceLoader(),
            new NfceSchemaProperties(tempDir.resolve("nfe_v4.00.xsd").toUri().toString())
        );

        String invalidXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <NFe xmlns="%s"><campoInexistente>1</campoInexistente></NFe>
            """.formatted(NFE_NAMESPACE);

        FiscalIntegrationException ex = assertThrows(
            FiscalIntegrationException.class,
            () -> validator.validate(invalidXml)
        );
        assertTrue(ex.getMessage().contains("schema XSD oficial"));
    }
}