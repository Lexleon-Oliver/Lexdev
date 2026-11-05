package net.ddns.lexdev.systempro_api.fiscal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;

/**
 * Converte snapshots fiscais persistidos em valores para o XML da NT 2026.008.
 * Não calcula nem presume tributos: somente lê dados auditáveis dos itens.
 * Sem snapshots, mantém a emissão legada. Snapshot parcial bloqueia a emissão.
 */
final class NfceLiquidSnapshotResolver {
    private NfceLiquidSnapshotResolver() { }

    static Map<Integer, NfceLiquidProductValues> fromSale(Sale sale) {
        if (sale == null || sale.getItems() == null || sale.getItems().isEmpty()) {
            return Map.of();
        }
        Map<Integer, NfceLiquidProductValues> resolved = new LinkedHashMap<>();
        for (SaleItem item : sale.getItems()) {
            if (item == null) {
                throw new FiscalIntegrationException("A venda contém item fiscal ausente.");
            }
            NfceLiquidProductValues liquid = item.getNfceLiquidSnapshot();
            if (liquid != null && resolved.putIfAbsent(item.getItemNumber(), liquid) != null) {
                throw new FiscalIntegrationException("Snapshot fiscal líquido possui numeração de item duplicada.");
            }
        }
        if (!resolved.isEmpty() && resolved.size() != sale.getItems().size()) {
            throw new FiscalIntegrationException(
                "Não é permitido emitir vProdLiq quando parte dos itens não possui snapshot tributário."
            );
        }
        return Collections.unmodifiableMap(resolved);
    }
}
