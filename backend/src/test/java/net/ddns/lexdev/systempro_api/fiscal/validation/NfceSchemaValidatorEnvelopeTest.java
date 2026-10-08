package net.ddns.lexdev.systempro_api.fiscal.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import net.ddns.lexdev.systempro_api.config.NfceSchemaProperties;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;

class NfceSchemaValidatorEnvelopeTest {

    private final NfceSchemaValidator validator = new NfceSchemaValidator(
        new DefaultResourceLoader(),
        new NfceSchemaProperties("classpath:/fiscal/nfce/PL_010f/nfe_v4.00.xsd")
    );

    @Test
    void deveUsarSchemaNfeDoPlParaValidarNfeContidaNoEnvelope() {
        String invalidNfe =
            "<enviNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                + "<idLote>1</idLote><indSinc>1</indSinc>"
                + "<NFe><infNFe versao=\"4.00\" Id=\"NFe"
                + "31261012345678000195650010000000011000000010"
                + "\"></infNFe></NFe>"
                + "</enviNFe>";

        // O ponto deste teste é provar que o XSD existe/carrega e que a NFe filha
        // é efetivamente validada. A NFe mínima é deliberadamente inválida.
        assertThrows(FiscalIntegrationException.class, () -> validator.validate(invalidNfe));
    }

    @Test
    void deveRecusarEnvelopeSemNfe() {
        String xml =
            "<enviNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                + "<idLote>1</idLote><indSinc>1</indSinc>"
                + "</enviNFe>";

        assertThrows(FiscalIntegrationException.class, () -> validator.validate(xml));
    }
}