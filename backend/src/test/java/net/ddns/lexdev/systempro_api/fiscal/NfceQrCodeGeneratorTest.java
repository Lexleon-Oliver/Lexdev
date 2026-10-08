package net.ddns.lexdev.systempro_api.fiscal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;

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
    void deveGerarQrCodeVersao3OfflineComCpfEAssinaturaRsaSha1() throws Exception {
        KeyPair pair = rsaKeyPair();
        ZonedDateTime emission = ZonedDateTime.of(2026, 10, 8, 12, 30, 0, 0, ZoneId.of("America/Sao_Paulo"));

        String url = generator.offlineUrl(
            ACCESS_KEY,
            FiscalEnvironment.HOMOLOGACAO,
            emission,
            new BigDecimal("3499.90"),
            "123.456.789-01",
            pair.getPrivate()
        );

        String prefix = NfceQrCodeGenerator.MG_QR_CODE_URL + "?p=";
        assertTrue(url.startsWith(prefix));
        String payload = url.substring(prefix.length());
        String[] parts = payload.split("\\|", -1);
        assertEquals(8, parts.length);
        assertEquals(ACCESS_KEY, parts[0]);
        assertEquals("3", parts[1]);
        assertEquals("2", parts[2]);
        assertEquals("08", parts[3]);
        assertEquals("3499.90", parts[4]);
        assertEquals("2", parts[5]);
        assertEquals("12345678901", parts[6]);

        String signedParameters = String.join("|", java.util.Arrays.copyOf(parts, 7));
        Signature verifier = Signature.getInstance("SHA1withRSA");
        verifier.initVerify(pair.getPublic());
        verifier.update(signedParameters.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(parts[7])));
    }

    @Test
    void deveGerarQrCodeOfflineSemDestinatarioPreservandoSeparadores() throws Exception {
        KeyPair pair = rsaKeyPair();
        String url = generator.offlineUrl(
            ACCESS_KEY,
            FiscalEnvironment.PRODUCAO,
            ZonedDateTime.of(2026, 10, 9, 8, 0, 0, 0, ZoneId.of("America/Sao_Paulo")),
            new BigDecimal("10.00"),
            null,
            pair.getPrivate()
        );
        assertTrue(url.contains("|09|10.00|||"));
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

    @Test
    void deveRejeitarDocumentoDeDestinatarioIncompativelComQrOffline() throws Exception {
        KeyPair pair = rsaKeyPair();
        assertThrows(
            FiscalIntegrationException.class,
            () -> generator.offlineUrl(
                ACCESS_KEY,
                FiscalEnvironment.HOMOLOGACAO,
                ZonedDateTime.now(),
                BigDecimal.ONE,
                "12345",
                pair.getPrivate()
            )
        );
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}