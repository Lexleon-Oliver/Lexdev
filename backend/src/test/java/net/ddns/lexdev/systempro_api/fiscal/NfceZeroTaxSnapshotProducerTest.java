package net.ddns.lexdev.systempro_api.fiscal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsCalculation;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsJurisdictionCalculation;

class NfceZeroTaxSnapshotProducerTest {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 5);

    private static FiscalEstablishment establishment(TaxRegime regime) {
        FiscalEstablishment establishment = new FiscalEstablishment(null);
        establishment.setTaxRegime(regime);
        return establishment;
    }

    private static SaleItem item(int number, String quantity, String unit, String total) {
        SaleItem item = new SaleItem();
        item.setItemNumber(number);
        item.setCodeSnapshot("ITEM-" + number);
        item.setCfopSnapshot("5102");
        item.setOriginSnapshot("0");
        item.setNcmSnapshot("10063021");
        item.setIcmsCstCsosnSnapshot("40");
        item.setPisCstSnapshot("06");
        item.setCofinsCstSnapshot("06");
        item.setIcmsRate(BigDecimal.ZERO);
        item.setPisRate(BigDecimal.ZERO);
        item.setCofinsRate(BigDecimal.ZERO);
        item.setQuantity(new BigDecimal(quantity));
        item.setUnitPrice(new BigDecimal(unit));
        item.setDiscount(BigDecimal.ZERO);
        item.setTotal(new BigDecimal(total));
        item.setIbsCbsCalculation(rtc(total, "0", "0"));
        return item;
    }

    private static IbsCbsCalculation rtc(String taxBase, String rate, String amount) {
        return new IbsCbsCalculation(
            "200", "200003", new BigDecimal(taxBase),
            new IbsCbsJurisdictionCalculation(new BigDecimal(rate), new BigDecimal(amount)),
            new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO),
            new BigDecimal(amount),
            new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO)
        );
    }

    private static Sale sale(SaleItem... items) {
        Sale sale = new Sale();
        sale.setSaleAt(Instant.parse("2026-11-05T15:00:00Z"));
        sale.setDiscount(BigDecimal.ZERO);
        for (SaleItem item : items) sale.addItem(item);
        return sale;
    }

    private static FiscalIntegrationException rejected(FiscalEstablishment establishment, Sale sale, LocalDate date) {
        return assertThrows(FiscalIntegrationException.class,
            () -> NfceZeroTaxSnapshotProducer.apply(establishment, sale, date));
    }

    @Test
    void todosItensElegiveisGeramSnapshotsComFonteIdentificavel() {
        SaleItem regular = item(1, "2", "12.00", "24.00");
        SaleItem weighed = item(2, "0.125", "10.60", "1.33");
        Sale sale = sale(regular, weighed);

        NfceZeroTaxSnapshotProducer.apply(establishment(TaxRegime.REGIME_NORMAL), sale, DATE);

        assertEquals(new BigDecimal("24.00"), regular.getNfceLiquidSnapshot().totalWithoutTaxes());
        assertEquals(new BigDecimal("12.00"), regular.getNfceLiquidSnapshot().unitWithoutTaxes());
        assertEquals(new BigDecimal("1.33"), weighed.getNfceLiquidSnapshot().totalWithoutTaxes());
        assertEquals(new BigDecimal("10.60"), weighed.getNfceLiquidSnapshot().unitWithoutTaxes());
        assertEquals(NfceZeroTaxSnapshotProducer.CALCULATION_SOURCE,
            weighed.getNfceLiquidCalculationSource());
        assertEquals(2, NfceLiquidSnapshotResolver.fromSale(sale).size());
    }

    @Test
    void deveFalharSemAlterarPrimeiroItemQuandoSegundoForTributado() {
        SaleItem first = item(1, "1", "10.00", "10.00");
        SaleItem second = item(2, "1", "5.00", "5.00");
        second.setIcmsCstCsosnSnapshot("00");
        Sale sale = sale(first, second);

        assertTrue(rejected(establishment(TaxRegime.REGIME_NORMAL), sale, DATE)
            .getMessage().contains("CST"));
        assertFalse(first.hasAnyNfceLiquidSnapshotData());
        assertFalse(second.hasAnyNfceLiquidSnapshotData());
    }

    @Test
    void nuncaDeveConsiderarTributacaoIntegralComoZeroTaxMesmoComSnapshotZerado() {
        SaleItem item = item(1, "1", "100.00", "100.00");
        item.setIbsCbsCalculation(new IbsCbsCalculation(
            "000", "000001", new BigDecimal("100.00"),
            new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO),
            new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO),
            BigDecimal.ZERO,
            new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO)
        ));
        assertTrue(rejected(establishment(TaxRegime.REGIME_NORMAL), sale(item), DATE)
            .getMessage().contains("classificação"));
        assertFalse(item.hasAnyNfceLiquidSnapshotData());
    }

    @Test
    void deveBloquearOutrasClassesComReducaoNaoComprovada() {
        SaleItem item = item(1, "1", "100.00", "100.00");
        item.setIbsCbsCalculation(new IbsCbsCalculation(
            "200", "200034", new BigDecimal("100.00"),
            new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO),
            new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO),
            BigDecimal.ZERO,
            new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO)
        ));
        rejected(establishment(TaxRegime.REGIME_NORMAL), sale(item), DATE);
    }

    @Test
    void deveBloquearAliquotaRtcPositivaMesmoQueArredondamentoDeTributoSejaZero() {
        SaleItem item = item(1, "1", "0.01", "0.01");
        item.setIbsCbsCalculation(rtc("0.01", "0.1", "0.00"));
        rejected(establishment(TaxRegime.REGIME_NORMAL), sale(item), DATE);
        assertNull(item.getNfceLiquidSnapshot());
    }

    @Test
    void deveBloquearValorRtcPositivoMesmoComAliquotaZerada() {
        SaleItem item = item(1, "1", "100.00", "100.00");
        item.setIbsCbsCalculation(rtc("100.00", "0", "0.01"));
        rejected(establishment(TaxRegime.REGIME_NORMAL), sale(item), DATE);
    }

    @Test
    void deveBloquearRegimeSimples() {
        rejected(establishment(TaxRegime.SIMPLES_NACIONAL),
            sale(item(1, "1", "10.00", "10.00")), DATE);
    }

    @Test
    void deveBloquearDescontoPorItemOuGlobal() {
        SaleItem item = item(1, "1", "10.00", "9.00");
        item.setDiscount(BigDecimal.ONE);
        rejected(establishment(TaxRegime.REGIME_NORMAL), sale(item), DATE);
        Sale noDiscount = sale(item(2, "1", "10.00", "10.00"));
        noDiscount.setDiscount(BigDecimal.ONE);
        rejected(establishment(TaxRegime.REGIME_NORMAL), noDiscount, DATE);
    }

    @Test
    void deveBloquearAliquotasAntigasIncoerentesComExencao() {
        SaleItem item = item(1, "1", "10.00", "10.00");
        item.setPisRate(new BigDecimal("1.65"));
        rejected(establishment(TaxRegime.REGIME_NORMAL), sale(item), DATE);
    }

    @Test
    void deveBloquearCfopOuOrigemDiferenteDoContrato() {
        SaleItem item = item(1, "1", "10.00", "10.00");
        item.setCfopSnapshot("6102");
        rejected(establishment(TaxRegime.REGIME_NORMAL), sale(item), DATE);
        item.setCfopSnapshot("5102");
        item.setOriginSnapshot("1");
        rejected(establishment(TaxRegime.REGIME_NORMAL), sale(item), DATE);
    }

    @Test
    void deveBloquearNcmForaDoRecorteVerificavelDoAnexoI() {
        SaleItem item = item(1, "1", "10.00", "10.00");
        item.setNcmSnapshot("61091000");
        assertTrue(rejected(establishment(TaxRegime.REGIME_NORMAL), sale(item), DATE)
            .getMessage().contains("NCM"));
    }

    @Test
    void deveBloquearDataDaVendaDivergenteDaOperacao() {
        Sale sale = sale(item(1, "1", "10.00", "10.00"));
        sale.setSaleAt(Instant.parse("2026-11-06T15:00:00Z"));
        rejected(establishment(TaxRegime.REGIME_NORMAL), sale, DATE);
    }

    @Test
    void deveBloquearForaDe2026() {
        rejected(establishment(TaxRegime.REGIME_NORMAL),
            sale(item(1, "1", "10.00", "10.00")), LocalDate.of(2027, 1, 1));
    }

    @Test
    void deveBloquearValorIncoerenteComQuantidade() {
        rejected(establishment(TaxRegime.REGIME_NORMAL),
            sale(item(1, "2", "10.00", "25.00")), DATE);
    }

    @Test
    void deveBloquearDadosRtcAusentesOuDivergentes() {
        SaleItem item = item(1, "1", "10.00", "10.00");
        item.setIbsCbsCalculation(null);
        rejected(establishment(TaxRegime.REGIME_NORMAL), sale(item), DATE);
        item.setIbsCbsCalculation(rtc("11.00", "0", "0"));
        rejected(establishment(TaxRegime.REGIME_NORMAL), sale(item), DATE);
    }

    @Test
    void deveBloquearRtcParcialComErroFiscalEmVezDeNullPointer() {
        SaleItem item = item(1, "1", "10.00", "10.00");
        item.setIbsCbsCalculation(null);
        // Sem RTC integral, o produtor deve falhar de forma controlada.
        assertTrue(rejected(establishment(TaxRegime.REGIME_NORMAL), sale(item), DATE)
            .getMessage().contains("RTC"));
    }

    @Test
    void deveBloquearNumeroDeItemDuplicadoAntesDeAlterarSnapshots() {
        SaleItem first = item(1, "1", "10.00", "10.00");
        SaleItem second = item(1, "1", "20.00", "20.00");
        rejected(establishment(TaxRegime.REGIME_NORMAL), sale(first, second), DATE);
        assertNull(first.getNfceLiquidSnapshot());
        assertNull(second.getNfceLiquidSnapshot());
    }

    @Test
    void naoDeveSobrescreverSnapshotDeOutraOrigem() {
        SaleItem item = item(1, "1", "10.00", "10.00");
        item.setNfceLiquidSnapshot(new NfceLiquidProductValues(BigDecimal.TEN, BigDecimal.TEN), "EXT-AUDIT:v1");
        rejected(establishment(TaxRegime.REGIME_NORMAL), sale(item), DATE);
        assertNotNull(item.getNfceLiquidSnapshot());
        assertEquals("EXT-AUDIT:v1", item.getNfceLiquidCalculationSource());
    }
}
