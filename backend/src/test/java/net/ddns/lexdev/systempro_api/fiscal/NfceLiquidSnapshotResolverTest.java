package net.ddns.lexdev.systempro_api.fiscal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;

class NfceLiquidSnapshotResolverTest {

    private static SaleItem item(int number, String quantity) {
        SaleItem item = new SaleItem();
        item.setItemNumber(number);
        item.setQuantity(new BigDecimal(quantity));
        return item;
    }

    @Test
    void vendaSemValoresFiscaisNaoInventaTotaisLiquidos() {
        Sale sale = new Sale();
        sale.addItem(item(1, "0.125"));
        sale.addItem(item(2, "0.125"));
        assertTrue(NfceLiquidSnapshotResolver.fromSale(sale).isEmpty());
    }

    @Test
    void guardaOrigemERecuperaDoisItensFracionarios() {
        Sale sale = new Sale();
        SaleItem one = item(1, "0.125");
        SaleItem two = item(2, "0.125");
        one.setNfceLiquidSnapshot(new NfceLiquidProductValues(
            new BigDecimal("8.0000000000"), new BigDecimal("1.00")), "RTC-AUDIT:v1");
        two.setNfceLiquidSnapshot(new NfceLiquidProductValues(
            new BigDecimal("7.20"), new BigDecimal("0.90")), "RTC-AUDIT:v1");
        sale.addItem(one);
        sale.addItem(two);
        Map<Integer, NfceLiquidProductValues> resolved = NfceLiquidSnapshotResolver.fromSale(sale);
        assertEquals(2, resolved.size());
        assertEquals(new BigDecimal("1.00"), resolved.get(1).totalWithoutTaxes());
        assertEquals(new BigDecimal("0.90"), resolved.get(2).totalWithoutTaxes());
        assertEquals("RTC-AUDIT:v1", one.getNfceLiquidCalculationSource());
        assertThrows(UnsupportedOperationException.class, () -> resolved.clear());
    }

    @Test
    void snapshotParcialNaoPodeGerarXml() {
        Sale sale = new Sale();
        SaleItem one = item(1, "0.125");
        one.setNfceLiquidSnapshot(new NfceLiquidProductValues(
            new BigDecimal("8.00"), new BigDecimal("1.00")), "RTC-AUDIT:v1");
        sale.addItem(one);
        sale.addItem(item(2, "0.125"));
        assertThrows(FiscalIntegrationException.class, () -> NfceLiquidSnapshotResolver.fromSale(sale));
    }

    @Test
    void snapshotSemIdentificacaoDeOrigemNaoEAdmitido() {
        SaleItem one = item(1, "0.125");
        assertThrows(FiscalIntegrationException.class, () -> one.setNfceLiquidSnapshot(
            new NfceLiquidProductValues(new BigDecimal("8"), BigDecimal.ONE), null));
        assertThrows(FiscalIntegrationException.class, () -> one.setNfceLiquidSnapshot(
            new NfceLiquidProductValues(new BigDecimal("8"), BigDecimal.ONE), "teste com espacos"));
    }

    @Test
    void revalidaQuantidadeMesmoDepoisDeSnapshot() {
        Sale sale = new Sale();
        SaleItem one = item(1, "0.125");
        one.setNfceLiquidSnapshot(new NfceLiquidProductValues(
            new BigDecimal("8"), BigDecimal.ONE), "RTC-AUDIT:v1");
        sale.addItem(one);
        one.setQuantity(new BigDecimal("0.150"));
        assertThrows(FiscalIntegrationException.class, () -> NfceLiquidSnapshotResolver.fromSale(sale));
    }

    @Test
    void recusaNumeroDuplicadoParaSnapshots() {
        Sale sale = new Sale();
        for (int n = 0; n < 2; n++) {
            SaleItem one = item(1, "0.125");
            one.setNfceLiquidSnapshot(new NfceLiquidProductValues(
                new BigDecimal("8"), BigDecimal.ONE), "RTC-AUDIT:v1");
            sale.addItem(one);
        }
        assertThrows(FiscalIntegrationException.class, () -> NfceLiquidSnapshotResolver.fromSale(sale));
    }

    @Test
    void podeLimparSnapshotParaNaoPreencherCamposNovos() {
        SaleItem one = item(1, "0.125");
        one.setNfceLiquidSnapshot(new NfceLiquidProductValues(
            new BigDecimal("8"), BigDecimal.ONE), "RTC-AUDIT:v1");
        one.setNfceLiquidSnapshot(null, null);
        assertNull(one.getNfceLiquidSnapshot());
        assertNull(one.getNfceLiquidCalculationSource());
    }
}
