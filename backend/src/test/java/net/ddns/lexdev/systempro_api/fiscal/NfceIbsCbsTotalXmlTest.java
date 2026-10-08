package net.ddns.lexdev.systempro_api.fiscal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsCalculation;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsJurisdictionCalculation;

class NfceIbsCbsTotalXmlTest {

    @Test
    void deveTotalizarExclusivamenteOsSnapshotsRtcDosItens() {
        SaleItem first = item(
            "60.00", "0.06", "0.00", "0.06", "0.54"
        );
        SaleItem second = item(
            "40.00", "0.04", "0.00", "0.04", "0.36"
        );

        assertEquals(
            "<IBSCBSTot>"
                + "<vBCIBSCBS>100.00</vBCIBSCBS>"
                + "<gIBS>"
                + "<gIBSUF><vDif>0.00</vDif><vDevTrib>0.00</vDevTrib><vIBSUF>0.10</vIBSUF></gIBSUF>"
                + "<gIBSMun><vDif>0.00</vDif><vDevTrib>0.00</vDevTrib><vIBSMun>0.00</vIBSMun></gIBSMun>"
                + "<vIBS>0.10</vIBS>"
                + "<vCredPres>0.00</vCredPres><vCredPresCondSus>0.00</vCredPresCondSus>"
                + "</gIBS>"
                + "<gCBS><vDif>0.00</vDif><vDevTrib>0.00</vDevTrib><vCBS>0.90</vCBS>"
                + "<vCredPres>0.00</vCredPres><vCredPresCondSus>0.00</vCredPresCondSus>"
                + "</gCBS>"
                + "</IBSCBSTot>",
            SefazMgNfceGateway.NfceXmlBuilder.ibsCbsTotalXml(List.of(first, second))
        );
    }

    @Test
    void naoDeveEmitirTotalRtcSemSnapshotRtc() {
        assertEquals(
            "",
            SefazMgNfceGateway.NfceXmlBuilder.ibsCbsTotalXml(List.of(new SaleItem(), new SaleItem()))
        );
    }

    @Test
    void deveIgnorarItemSemSnapshotAoTotalizarDocumentoMisto() {
        SaleItem rtc = item("100.00", "0.10", "0.00", "0.10", "0.90");

        String xml = SefazMgNfceGateway.NfceXmlBuilder.ibsCbsTotalXml(
            List.of(new SaleItem(), rtc)
        );

        assertEquals(
            "<IBSCBSTot>"
                + "<vBCIBSCBS>100.00</vBCIBSCBS>"
                + "<gIBS>"
                + "<gIBSUF><vDif>0.00</vDif><vDevTrib>0.00</vDevTrib><vIBSUF>0.10</vIBSUF></gIBSUF>"
                + "<gIBSMun><vDif>0.00</vDif><vDevTrib>0.00</vDevTrib><vIBSMun>0.00</vIBSMun></gIBSMun>"
                + "<vIBS>0.10</vIBS>"
                + "<vCredPres>0.00</vCredPres><vCredPresCondSus>0.00</vCredPresCondSus>"
                + "</gIBS>"
                + "<gCBS><vDif>0.00</vDif><vDevTrib>0.00</vDevTrib><vCBS>0.90</vCBS>"
                + "<vCredPres>0.00</vCredPres><vCredPresCondSus>0.00</vCredPresCondSus>"
                + "</gCBS>"
                + "</IBSCBSTot>",
            xml
        );
    }

    private static SaleItem item(
        String taxBase,
        String ibsUfAmount,
        String ibsMunicipalAmount,
        String ibsTotal,
        String cbsAmount
    ) {
        SaleItem item = new SaleItem();
        item.setIbsCbsCalculation(new IbsCbsCalculation(
            "000",
            "000001",
            new BigDecimal(taxBase),
            new IbsCbsJurisdictionCalculation(new BigDecimal("0.100000"), new BigDecimal(ibsUfAmount)),
            new IbsCbsJurisdictionCalculation(new BigDecimal("0.000000"), new BigDecimal(ibsMunicipalAmount)),
            new BigDecimal(ibsTotal),
            new IbsCbsJurisdictionCalculation(new BigDecimal("0.900000"), new BigDecimal(cbsAmount))
        ));
        return item;
    }
}
