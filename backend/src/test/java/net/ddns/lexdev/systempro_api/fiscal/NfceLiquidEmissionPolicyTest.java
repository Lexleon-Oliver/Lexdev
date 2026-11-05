package net.ddns.lexdev.systempro_api.fiscal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.config.NfceSchemaProperties;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;

class NfceLiquidEmissionPolicyTest {
    private static final String LEGACY = "classpath:/fiscal/nfce/PL_010f/nfe_v4.00.xsd";
    private static final String CURRENT = "classpath:/fiscal/nfce/PL_010g/nfe_v4.00.xsd";

    private static SaleItem item(int n) {
        SaleItem item = new SaleItem();
        item.setItemNumber(n);
        item.setQuantity(new BigDecimal("0.125"));
        return item;
    }

    private static void snapshot(SaleItem item, String unit, String total) {
        item.setNfceLiquidSnapshot(
            new NfceLiquidProductValues(new BigDecimal(unit), new BigDecimal(total)),
            "RTC-AUDIT:v1"
        );
    }

    private static Sale sale(boolean firstHasSnapshot, boolean secondHasSnapshot) {
        Sale sale = new Sale();
        SaleItem one = item(1);
        SaleItem two = item(2);
        if (firstHasSnapshot) snapshot(one, "8.00", "1.00");
        if (secondHasSnapshot) snapshot(two, "7.20", "0.90");
        sale.addItem(one);
        sale.addItem(two);
        return sale;
    }

    @Test
    void fluxoPadraoNuncaInventaValoresLiquidos() {
        assertTrue(NfceLiquidEmissionPolicy.forXml(sale(false, false),
            new NfceSchemaProperties(LEGACY)).isEmpty());
    }

    @Test
    void desabilitadoNaoEmiteMesmoComSnapshotsCompletos() {
        assertTrue(NfceLiquidEmissionPolicy.forXml(sale(true, true),
            new NfceSchemaProperties(CURRENT, false)).isEmpty());
    }

    @Test
    void habilitadoExigePl010g() {
        assertThrows(FiscalIntegrationException.class, () -> NfceLiquidEmissionPolicy.forXml(
            sale(true, true), new NfceSchemaProperties(LEGACY, true)));
    }

    @Test
    void habilitadoNaoAceitaCaminhoNaoHomologadoParaSchema() {
        assertThrows(FiscalIntegrationException.class, () -> NfceLiquidEmissionPolicy.forXml(
            sale(true, true), new NfceSchemaProperties("file:/tmp/nfe_v4.00.xsd", true)));
    }

    @Test
    void habilitadoExigeSnapshotsPresentes() {
        assertThrows(FiscalIntegrationException.class, () -> NfceLiquidEmissionPolicy.forXml(
            sale(false, false), new NfceSchemaProperties(CURRENT, true)));
    }

    @Test
    void snapshotParcialBloqueiaMesmoComFuncionalidadeDesligada() {
        assertThrows(FiscalIntegrationException.class, () -> NfceLiquidEmissionPolicy.forXml(
            sale(true, false), new NfceSchemaProperties(LEGACY, false)));
    }

    @Test
    void snapshotParcialBloqueiaComFuncionalidadeLigada() {
        assertThrows(FiscalIntegrationException.class, () -> NfceLiquidEmissionPolicy.forXml(
            sale(true, false), new NfceSchemaProperties(CURRENT, true)));
    }

    @Test
    void habilitadoComPl010gConsomeSnapshotsPersistidosSemRecalcular() {
        Map<Integer, NfceLiquidProductValues> values = NfceLiquidEmissionPolicy.forXml(
            sale(true, true), new NfceSchemaProperties(CURRENT, true));
        assertEquals(2, values.size());
        assertEquals(new BigDecimal("1.00"), values.get(1).totalWithoutTaxes());
        assertEquals(new BigDecimal("0.90"), values.get(2).totalWithoutTaxes());
        assertThrows(UnsupportedOperationException.class, () -> values.clear());
    }

    @Test
    void vendaSemItensNaoPodeSerEmitida() {
        assertThrows(FiscalIntegrationException.class, () -> NfceLiquidEmissionPolicy.forXml(
            new Sale(), new NfceSchemaProperties(CURRENT, true)));
    }
}
