package net.ddns.lexdev.systempro_api.config;

public final class CpfCnpjNormalizer {

    private CpfCnpjNormalizer() {
    }

    public static String normalize(String value) {

        if (value == null) {
            return null;
        }

        return value.replaceAll("\\D", "");
    }
}
