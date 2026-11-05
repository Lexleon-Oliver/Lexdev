package net.ddns.lexdev.systempro_api.fiscal;

import java.util.Map;

import net.ddns.lexdev.systempro_api.config.NfceSchemaProperties;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;

/**
 * Ponte segura entre snapshots líquidos persistidos e geração do XML fiscal.
 * Não produz valores tributários e não ativa automaticamente campos opcionais.
 * O opt-in só é permitido com o schema oficial PL_010g empacotado e validado.
 */
final class NfceLiquidEmissionPolicy {
    private static final String PL_010G_SCHEMA =
        "classpath:/fiscal/nfce/PL_010g/nfe_v4.00.xsd";

    private NfceLiquidEmissionPolicy() { }

    static Map<Integer, NfceLiquidProductValues> forXml(
        Sale sale, NfceSchemaProperties configuration
    ) {
        if (sale == null || sale.getItems() == null || sale.getItems().isEmpty()) {
            throw new FiscalIntegrationException("A NFC-e precisa ter itens para validação de snapshots fiscais.");
        }
        if (configuration == null) {
            throw new FiscalIntegrationException("Configuração do schema fiscal não encontrada.");
        }

        // Mesmo com a funcionalidade desligada, um snapshot PARCIAL precisa
        // ser detectado; não ocultar dados tributários inconsistentes no banco.
        Map<Integer, NfceLiquidProductValues> snapshots = NfceLiquidSnapshotResolver.fromSale(sale);

        if (!configuration.emitLiquidValues()) {
            return Map.of(); // Comportamento legado: nenhuma tag vProdLiq.
        }
        if (!PL_010G_SCHEMA.equals(configuration.location())) {
            throw new FiscalIntegrationException(
                "A emissão de valores líquidos exige o XSD PL_010g oficial integrado ao SystemPro."
            );
        }
        if (snapshots.size() != sale.getItems().size()) {
            throw new FiscalIntegrationException(
                "A emissão de vProdLiq está habilitada, mas a venda não tem snapshot fiscal completo em todos os itens."
            );
        }
        return snapshots;
    }
}
