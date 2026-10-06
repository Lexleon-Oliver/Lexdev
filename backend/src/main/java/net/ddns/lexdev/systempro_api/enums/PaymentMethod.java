package net.ddns.lexdev.systempro_api.enums;

public enum PaymentMethod {
    DINHEIRO("01"),
    CREDITO("03"),
    DEBITO("04"),
    PIX("17"),
    OUTRO("99");

    private final String fiscalCode;

    PaymentMethod(String fiscalCode) {
        this.fiscalCode = fiscalCode;
    }

    public String getFiscalCode() {
        return fiscalCode;
    }
}
