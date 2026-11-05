package net.ddns.lexdev.systempro_api.fiscal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;

import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsCalculation;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsJurisdictionCalculation;

class NfceIbsCbsXmlSerializationTest {

    @Test
    void deveSerializarExclusivamenteOSnapshotRtcDoItem() {
        SaleItem item = new SaleItem();
        item.setIbsCbsCalculation(new IbsCbsCalculation(
            "000",
            "000001",
            new BigDecimal("100.00"),
            new IbsCbsJurisdictionCalculation(new BigDecimal("0.100000"), new BigDecimal("0.10")),
            new IbsCbsJurisdictionCalculation(new BigDecimal("0.000000"), new BigDecimal("0.00")),
            new BigDecimal("0.10"),
            new IbsCbsJurisdictionCalculation(new BigDecimal("0.900000"), new BigDecimal("0.90"))
        ));

        assertEquals(
            "<IBSCBS>"
                + "<CST>000</CST>"
                + "<cClassTrib>000001</cClassTrib>"
                + "<gIBSCBS>"
                + "<vBC>100.00</vBC>"
                + "<gIBSUF><pIBSUF>0.1000</pIBSUF><vIBSUF>0.10</vIBSUF></gIBSUF>"
                + "<gIBSMun><pIBSMun>0.0000</pIBSMun><vIBSMun>0.00</vIBSMun></gIBSMun>"
                + "<vIBS>0.10</vIBS>"
                + "<gCBS><pCBS>0.9000</pCBS><vCBS>0.90</vCBS></gCBS>"
                + "</gIBSCBS>"
                + "</IBSCBS>",
            SefazMgNfceGateway.NfceXmlBuilder.ibsCbsXml(item)
        );
    }

    @Test
    void cestaBasicaUsaAliquotasNominaisEReducaoIntegralEm2026() {
        Sale sale = new Sale();
        sale.setSaleAt(Instant.parse("2026-11-05T15:00:00Z"));
        SaleItem item = new SaleItem();
        sale.addItem(item);
        item.setIbsCbsCalculation(new IbsCbsCalculation(
            "200", "200003", new BigDecimal("10.00"),
            new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO),
            new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO),
            BigDecimal.ZERO,
            new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO)
        ));

        String xml = SefazMgNfceGateway.NfceXmlBuilder.ibsCbsXml(item);
        String gRed = "<gRed><pRedAliq>100.0000</pRedAliq>"
            + "<pAliqEfet>0.0000</pAliqEfet></gRed>";
        assertEquals(3, xml.split("<gRed>", -1).length - 1);
        assertEquals(3, xml.split(java.util.regex.Pattern.quote(gRed), -1).length - 1);
        org.junit.jupiter.api.Assertions.assertTrue(xml.contains("<pIBSUF>0.1000</pIBSUF>"));
        org.junit.jupiter.api.Assertions.assertTrue(xml.contains("<pIBSMun>0.0000</pIBSMun>"));
        org.junit.jupiter.api.Assertions.assertTrue(xml.contains("<pCBS>0.9000</pCBS>"));
    }

    @Test
    void cestaBasicaRejeitaSnapshotComAliquotaEfetivaPositiva() {
        Sale sale = new Sale();
        sale.setSaleAt(Instant.parse("2026-11-05T15:00:00Z"));
        SaleItem item = new SaleItem();
        sale.addItem(item);
        item.setIbsCbsCalculation(new IbsCbsCalculation(
            "200", "200003", new BigDecimal("100.00"),
            new IbsCbsJurisdictionCalculation(new BigDecimal("0.1"), new BigDecimal("0.1")),
            new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO),
            new BigDecimal("0.1"),
            new IbsCbsJurisdictionCalculation(BigDecimal.ZERO, BigDecimal.ZERO)
        ));
        assertThrows(FiscalIntegrationException.class,
            () -> SefazMgNfceGateway.NfceXmlBuilder.ibsCbsXml(item));
    }

    @Test
    void naoDeveEmitirGrupoRtcQuandoItemNaoPossuiSnapshot() {
        assertEquals("", SefazMgNfceGateway.NfceXmlBuilder.ibsCbsXml(new SaleItem()));
    }
}
