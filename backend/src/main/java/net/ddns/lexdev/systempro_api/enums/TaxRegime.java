package net.ddns.lexdev.systempro_api.enums;

public enum TaxRegime {
    SIMPLES_NACIONAL("1", true),
    REGIME_NORMAL("3", false);

    private final String sefazCrtCode;
    private final boolean usesCsosn;

    TaxRegime(String sefazCrtCode, boolean usesCsosn) {
        this.sefazCrtCode = sefazCrtCode;
        this.usesCsosn = usesCsosn;
    }

    public String sefazCrtCode() {
        return sefazCrtCode;
    }

    public boolean usesCsosn() {
        return usesCsosn;
    }
}