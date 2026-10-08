package net.ddns.lexdev.systempro_api.fiscal.rtc;

import java.time.LocalDate;

/**
 * Fronteira para o catálogo oficial/evolutivo CST + cClassTrib.
 * A implementação concreta será ligada à tabela oficial na etapa 24.3.
 */
public interface IbsCbsTaxRuleResolver {
    IbsCbsTaxRule resolve(String cst, String cClassTrib, LocalDate operationDate, int fiscalDocumentModel);
}
