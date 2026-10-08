package net.ddns.lexdev.systempro_api.fiscal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import org.springframework.stereotype.Component;

import net.ddns.lexdev.systempro_api.enums.FiscalEnvironment;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;

@Component
public class NfceQrCodeGenerator {

    static final String MG_QR_CODE_URL =
        "https://portalsped.fazenda.mg.gov.br/portalnfce/sistema/qrcode.xhtml";
    static final String MG_PRODUCTION_CONSULTATION_URL =
        "https://portalsped.fazenda.mg.gov.br/portalnfce";
    static final String MG_HOMOLOGATION_CONSULTATION_URL =
        "https://hportalsped.fazenda.mg.gov.br/portalnfce";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd");

    public String onlineUrl(String accessKey, FiscalEnvironment environment) {
        validateAccessKey(accessKey);
        return MG_QR_CODE_URL + "?p=" + accessKey + "|3|" + environmentCode(environment);
    }

    /**
     * QR Code v3 da NFC-e emitida em contingência offline (tpEmis=9).
     *
     * A assinatura é RSA-SHA1 em Base64 sobre a concatenação dos sete
     * primeiros parâmetros, preservando os separadores '|', usando a mesma
     * chave privada do certificado que assina a NFC-e.
     */
    public String offlineUrl(
        String accessKey,
        FiscalEnvironment environment,
        ZonedDateTime emissionDateTime,
        BigDecimal total,
        String recipientDocument,
        PrivateKey privateKey
    ) {
        validateAccessKey(accessKey);
        if (emissionDateTime == null) {
            throw new FiscalIntegrationException("A data de emissão é obrigatória para gerar o QR Code offline da NFC-e.");
        }
        if (total == null || total.signum() < 0) {
            throw new FiscalIntegrationException("O valor total da NFC-e é obrigatório para gerar o QR Code offline.");
        }
        if (privateKey == null) {
            throw new FiscalIntegrationException("A chave privada do certificado A1 é obrigatória para assinar o QR Code offline.");
        }

        Recipient recipient = recipient(recipientDocument);
        String parameters = String.join("|",
            accessKey,
            "3",
            environmentCode(environment),
            DAY.format(emissionDateTime),
            total.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            recipient.type(),
            recipient.document()
        );

        return MG_QR_CODE_URL + "?p=" + parameters + "|" + sign(parameters, privateKey);
    }

    public String consultationUrl(FiscalEnvironment environment) {
        if (environment == null) {
            throw new FiscalIntegrationException("O ambiente fiscal é obrigatório para gerar a consulta da NFC-e.");
        }
        return environment == FiscalEnvironment.PRODUCAO
            ? MG_PRODUCTION_CONSULTATION_URL
            : MG_HOMOLOGATION_CONSULTATION_URL;
    }

    private static String sign(String parameters, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initSign(privateKey);
            signature.update(parameters.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception ex) {
            throw new FiscalIntegrationException("Não foi possível assinar o QR Code offline da NFC-e.", ex);
        }
    }

    private static Recipient recipient(String document) {
        if (document == null || document.isBlank()) {
            return new Recipient("", "");
        }
        String digits = document.replaceAll("\\D", "");
        if (digits.length() == 14) {
            return new Recipient("1", digits);
        }
        if (digits.length() == 11) {
            return new Recipient("2", digits);
        }
        throw new FiscalIntegrationException(
            "O destinatário do QR Code offline deve possuir CPF ou CNPJ válido quanto ao formato."
        );
    }

    private static String environmentCode(FiscalEnvironment environment) {
        if (environment == null) {
            throw new FiscalIntegrationException("O ambiente fiscal é obrigatório para gerar o QR Code da NFC-e.");
        }
        return environment == FiscalEnvironment.PRODUCAO ? "1" : "2";
    }

    private static void validateAccessKey(String accessKey) {
        if (accessKey == null || !accessKey.matches("\\d{44}")) {
            throw new FiscalIntegrationException("A chave de acesso da NFC-e deve possuir 44 dígitos para gerar o QR Code.");
        }
    }

    private record Recipient(String type, String document) {}
}