package net.ddns.lexdev.systempro_api.fiscal;

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

    public String onlineUrl(String accessKey, FiscalEnvironment environment) {
        validateAccessKey(accessKey);
        return MG_QR_CODE_URL + "?p=" + accessKey + "|3|" + environmentCode(environment);
    }

    public String consultationUrl(FiscalEnvironment environment) {
        if (environment == null) {
            throw new FiscalIntegrationException("O ambiente fiscal é obrigatório para gerar a consulta da NFC-e.");
        }
        return environment == FiscalEnvironment.PRODUCAO
            ? MG_PRODUCTION_CONSULTATION_URL
            : MG_HOMOLOGATION_CONSULTATION_URL;
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
}
