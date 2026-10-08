package net.ddns.lexdev.systempro_api.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;

class FiscalCertificateValidationTest {

    private static final String PASSWORD = "changeit";

    @TempDir
    Path tempDir;

    @Test
    void shouldAcceptValidPkcs12WithPrivateKeyAndX509Certificate() throws Exception {
        Path p12 = generateCertificate("valid", null, 365);

        assertDoesNotThrow(() -> FiscalEstablishmentService.FiscalCertificateValidator.validate(p12, PASSWORD));
    }

    @Test
    void shouldRejectExpiredCertificateExplicitly() throws Exception {
        Path p12 = generateCertificate("expired", "2020/01/01 00:00:00", 1);

        FiscalConfigurationException ex = assertThrows(
            FiscalConfigurationException.class,
            () -> FiscalEstablishmentService.FiscalCertificateValidator.validate(p12, PASSWORD)
        );

        assertEquals("O certificado A1 está expirado.", ex.getMessage());
    }

    @Test
    void shouldRejectCertificateWithWrongPassword() throws Exception {
        Path p12 = generateCertificate("wrong-password", null, 365);

        FiscalConfigurationException ex = assertThrows(
            FiscalConfigurationException.class,
            () -> FiscalEstablishmentService.FiscalCertificateValidator.validate(p12, "senha-incorreta")
        );

        assertEquals("O certificado A1 não pôde ser aberto com a senha informada.", ex.getMessage());
    }

    @Test
    void shouldRejectInvalidPkcs12Content() throws Exception {
        Path invalid = tempDir.resolve("invalid.p12");
        Files.writeString(invalid, "isto-nao-e-um-pkcs12");

        FiscalConfigurationException ex = assertThrows(
            FiscalConfigurationException.class,
            () -> FiscalEstablishmentService.FiscalCertificateValidator.validate(invalid, PASSWORD)
        );

        assertEquals("O certificado A1 não pôde ser aberto com a senha informada.", ex.getMessage());
    }

    private Path generateCertificate(String alias, String startDate, int validityDays) throws Exception {
        Path output = tempDir.resolve(alias + ".p12");
        Path keytool = Path.of(
            System.getProperty("java.home"),
            "bin",
            System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool"
        );

        var command = new java.util.ArrayList<String>();
        command.add(keytool.toString());
        command.add("-genkeypair");
        command.add("-alias");
        command.add(alias);
        command.add("-keyalg");
        command.add("RSA");
        command.add("-keysize");
        command.add("2048");
        command.add("-dname");
        command.add("CN=SystemPro Fiscal Test, O=Lexdev, C=BR");
        command.add("-storetype");
        command.add("PKCS12");
        command.add("-keystore");
        command.add(output.toString());
        command.add("-storepass");
        command.add(PASSWORD);
        command.add("-keypass");
        command.add(PASSWORD);
        command.add("-validity");
        command.add(Integer.toString(validityDays));
        if (startDate != null) {
            command.add("-startdate");
            command.add(startDate);
        }
        command.add("-noprompt");

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String outputText = new String(process.getInputStream().readAllBytes());
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("keytool falhou ao preparar certificado de teste: " + outputText);
        }
        return output;
    }
}
