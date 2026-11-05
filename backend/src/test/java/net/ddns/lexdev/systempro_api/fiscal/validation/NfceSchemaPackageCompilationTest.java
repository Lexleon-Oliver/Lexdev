package net.ddns.lexdev.systempro_api.fiscal.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Test;

/**
 * Garante que cada pacote fiscal realmente presente no classpath seja
 * compilavel com todas as suas dependencias XSD. Nao muda o pacote ativo.
 */
class NfceSchemaPackageCompilationTest {

    @Test
    void deveCompilarPacotesDeSchemasDisponiveisNoClasspath() {
        int encontrados = 0;
        for (String pacote : new String[] {"PL_010f", "PL_010g"}) {
            String recurso = "fiscal/nfce/" + pacote + "/nfe_v4.00.xsd";
            URL url = getClass().getClassLoader().getResource(recurso);
            if (url == null) {
                continue;
            }
            encontrados++;
            assertDoesNotThrow(() -> {
                SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar");
                factory.newSchema(url);
            }, "Pacote " + pacote + " ausente/inconsistente em " + recurso);
        }
        assertTrue(encontrados >= 1, "Nenhum pacote XSD fiscal disponivel para validar");
    }
}
