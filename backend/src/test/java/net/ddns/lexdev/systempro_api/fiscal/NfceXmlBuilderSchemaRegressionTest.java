package net.ddns.lexdev.systempro_api.fiscal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.domain.AuditableEntity;
import net.ddns.lexdev.systempro_api.domain.Company;
import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.LegalEntity;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.PersonAddress;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.domain.SalePayment;
import net.ddns.lexdev.systempro_api.enums.AddressType;
import net.ddns.lexdev.systempro_api.enums.FiscalEnvironment;
import net.ddns.lexdev.systempro_api.enums.PaymentMethod;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;

class NfceXmlBuilderSchemaRegressionTest {

    @Test
    void deveEmitirCamposBasicosNaFormaEOrdemAceitasPeloSchema() throws Exception {
        FiscalEstablishment establishment = establishment();
        Sale sale = new Sale();
        sale.setFiscalEstablishment(establishment);
        sale.setSubtotal(new BigDecimal("10.00"));
        sale.setDiscount(new BigDecimal("1.00"));
        sale.setTotal(new BigDecimal("9.00"));

        SaleItem item = new SaleItem();
        item.setItemNumber(1);
        item.setCodeSnapshot("P1");
        item.setNameSnapshot("PRODUTO");
        item.setUnitSnapshot("UN");
        item.setNcmSnapshot("61091000");
        item.setOriginSnapshot("0");
        item.setCfopSnapshot("5102");
        item.setIcmsCstCsosnSnapshot("40");
        item.setPisCstSnapshot("06");
        item.setCofinsCstSnapshot("06");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("10.00"));
        item.setDiscount(new BigDecimal("1.00"));
        item.setTotal(new BigDecimal("9.00"));
        sale.addItem(item);

        SalePayment payment = new SalePayment();
        payment.setPaymentMethod(PaymentMethod.DINHEIRO);
        payment.setAmount(new BigDecimal("9.00"));
        sale.addPayment(payment);

        FiscalDocument document = new FiscalDocument();
        document.setSale(sale);
        document.setEstablishment(establishment);
        document.setSeries(1);
        document.setNumber(1L);
        setId(document, 1L);

        String accessKey = "31261012345678000195650010000000011000000010";
        String xml = SefazMgNfceGateway.NfceXmlBuilder.build(
            establishment, sale, document, accessKey, "00000001", 1,
            "2026-10-08T00:00:00-03:00", "4.00", "SystemPro 1.0",
            "https://portalsped.fazenda.mg.gov.br/portalnfce/sistema/qrcode.xhtml?p="
                + accessKey + "|3|2",
            "https://hportalsped.fazenda.mg.gov.br/portalnfce"
        );

        assertTrue(xml.contains("<serie>1</serie>"));
        assertFalse(xml.contains("<serie>001</serie>"));
        assertTrue(xml.contains("<tpEmis>1</tpEmis><cDV>0</cDV><tpAmb>2</tpAmb>"));
        assertTrue(xml.contains("<qCom>1.0000</qCom>"));
        assertTrue(xml.contains("<qTrib>1.0000</qTrib>"));

        int eanTrib = xml.indexOf("<cEANTrib>SEM GTIN</cEANTrib>");
        int discount = xml.indexOf("<vDesc>1.00</vDesc>");
        assertTrue(eanTrib >= 0 && discount > eanTrib);
    }

    private static FiscalEstablishment establishment() {
        Person person = new Person();
        person.setTipoPessoa(TipoPessoa.PJ);
        person.setName("EMPRESA TESTE");
        person.setCpfCnpj("12345678000195");

        LegalEntity legalEntity = new LegalEntity();
        legalEntity.setPerson(person);
        legalEntity.setNomeFantasia("SYSTEMPRO TESTE");
        legalEntity.setInscricaoEstadual("0623079040081");
        person.setLegalEntity(legalEntity);

        PersonAddress address = new PersonAddress();
        address.setType(AddressType.COMERCIAL);
        address.setCep("30140071");
        address.setLogradouro("AV AFONSO PENA");
        address.setNumero("1000");
        address.setBairro("CENTRO");
        address.setCidade("BELO HORIZONTE");
        address.setUf("MG");
        address.setPrincipal(true);
        person.addAddress(address);

        FiscalEstablishment establishment = new FiscalEstablishment(new Company(person));
        establishment.setMunicipalityIbgeCode("3106200");
        establishment.setTaxRegime(TaxRegime.REGIME_NORMAL);
        establishment.setEnvironment(FiscalEnvironment.HOMOLOGACAO);
        establishment.setSeries(1);
        return establishment;
    }

    private static void setId(AuditableEntity entity, Long id) throws Exception {
        Field idField = AuditableEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}