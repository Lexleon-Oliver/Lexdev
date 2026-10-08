package net.ddns.lexdev.systempro_api.fiscal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.enums.FiscalEnvironment;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;

class NfceQrCodeGeneratorTest {

    private static final String ACCESS_KEY = "31261012345678000190650010000001231123456789";

    private final NfceQrCodeGenerator generator = new NfceQrCodeGenerator();

    @Test
    void deveGerarQrCodeVersao3OnlineEmHomologacaoSemCsc() {
        assertEquals(
            "https://portalsped.fazenda.mg.gov.br/portalnfce/sistema/qrcode.xhtml?p="
                + ACCESS_KEY + "|3|2",
            generator.onlineUrl(ACCESS_KEY, FiscalEnvironment.HOMOLOGACAO)
        );
    }

    @Test
    void deveGerarQrCodeVersao3OnlineEmProducaoSemCsc() {
        assertEquals(
            "https://portalsped.fazenda.mg.gov.br/portalnfce/sistema/qrcode.xhtml?p="
                + ACCESS_KEY + "|3|1",
            generator.onlineUrl(ACCESS_KEY, FiscalEnvironment.PRODUCAO)
        );
    }

    @Test
    void deveUsarPortalDeConsultaCorrespondenteAoAmbiente() {
        assertEquals(
            "https://hportalsped.fazenda.mg.gov.br/portalnfce",
            generator.consultationUrl(FiscalEnvironment.HOMOLOGACAO)
        );
        assertEquals(
            "https://portalsped.fazenda.mg.gov.br/portalnfce",
            generator.consultationUrl(FiscalEnvironment.PRODUCAO)
        );
    }

    @Test
    void deveRejeitarChaveDeAcessoInvalida() {
        assertThrows(
            FiscalIntegrationException.class,
            () -> generator.onlineUrl("123", FiscalEnvironment.HOMOLOGACAO)
        );
    }
}
