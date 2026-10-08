package net.ddns.lexdev.systempro_api.fiscal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

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
    void naoDeveEmitirGrupoRtcQuandoItemNaoPossuiSnapshot() {
        assertEquals("", SefazMgNfceGateway.NfceXmlBuilder.ibsCbsXml(new SaleItem()));
    }
}
