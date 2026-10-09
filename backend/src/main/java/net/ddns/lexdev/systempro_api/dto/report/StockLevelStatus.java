package net.ddns.lexdev.systempro_api.dto.report;

public enum StockLevelStatus {
    NOT_CONTROLLED,
    NOT_INITIALIZED,
    OUT_OF_STOCK,
    BELOW_MINIMUM,
    REORDER,
    NORMAL,
    ABOVE_MAXIMUM
}