package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import net.ddns.lexdev.systempro_api.domain.Company;
import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.FiscalEmissionType;
import net.ddns.lexdev.systempro_api.enums.FiscalEnvironment;
import net.ddns.lexdev.systempro_api.enums.SaleStatus;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.fiscal.NfceIssueResult;
import net.ddns.lexdev.systempro_api.fiscal.SefazNfceGateway;
import net.ddns.lexdev.systempro_api.integration.IntegrationTestBase;
import net.ddns.lexdev.systempro_api.repository.FiscalDocumentRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalEstablishmentRepository;
import net.ddns.lexdev.systempro_api.repository.SaleRepository;

class FiscalAuthorizationPersistenceIntegrationTest extends IntegrationTestBase {

    private static final String ACCESS_KEY = "31261012345678000199650010000001231000000017";
    private static final String SIGNED_XML = "<NFe Id=\"NFe" + ACCESS_KEY + "\"><Signature/></NFe>";
    private static final String RESPONSE_XML = "<retEnviNFe><cStat>100</cStat></retEnviNFe>";
    private static final String PROTOCOL = "131260000000001";
    private static final Instant ISSUED_AT = Instant.parse("2026-10-07T22:30:00Z");

    @Autowired private SaleService saleService;
    @Autowired private SaleRepository saleRepository;
    @Autowired private FiscalDocumentRepository fiscalDocumentRepository;
    @Autowired private FiscalEstablishmentRepository establishmentRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private SefazNfceGateway gateway;
    @MockitoBean private FiscalEstablishmentService fiscalEstablishmentService;

    @Test
    void devePersistirAutorizacaoFiscalCompletaAposCommitENovaLeitura() {
        Fixture fixture = createFixture();

        when(fiscalEstablishmentService.requireDetailed(fixture.establishmentId()))
            .thenReturn(establishmentRepository.findById(fixture.establishmentId()).orElseThrow());
        when(gateway.authorize(any(), any(), any())).thenReturn(new NfceIssueResult(
            FiscalDocumentStatus.AUTORIZADA,
            ACCESS_KEY,
            SIGNED_XML,
            RESPONSE_XML,
            PROTOCOL,
            null,
            "Autorizado o uso da NF-e",
            ISSUED_AT
        ));

        saleService.issue(fixture.saleId());

        PersistedState persisted = inTransaction(() -> {
            entityManager.clear();
            FiscalDocument document = fiscalDocumentRepository.findBySaleId(fixture.saleId()).orElseThrow();
            Sale sale = saleRepository.findById(fixture.saleId()).orElseThrow();
            return new PersistedState(
                document.getStatus(),
                sale.getStatus(),
                document.getAccessKey(),
                document.getXml(),
                document.getResponseXml(),
                document.getProtocol(),
                document.getReceiptNumber(),
                document.getReason(),
                document.getIssuedAt()
            );
        });

        assertThat(persisted.documentStatus()).isEqualTo(FiscalDocumentStatus.AUTORIZADA);
        assertThat(persisted.saleStatus()).isEqualTo(SaleStatus.FISCALIZADA);
        assertThat(persisted.accessKey()).isEqualTo(ACCESS_KEY);
        assertThat(persisted.xml()).isEqualTo(SIGNED_XML);
        assertThat(persisted.responseXml()).isEqualTo(RESPONSE_XML);
        assertThat(persisted.protocol()).isEqualTo(PROTOCOL);
        assertThat(persisted.receiptNumber()).isNull();
        assertThat(persisted.reason()).isEqualTo("Autorizado o uso da NF-e");
        assertThat(persisted.issuedAt()).isEqualTo(ISSUED_AT);
    }

    private Fixture createFixture() {
        return inTransaction(() -> {
            long suffix = Math.floorMod(System.nanoTime(), 1_000_000_000L);

            Person person = new Person();
            person.setTipoPessoa(TipoPessoa.PJ);
            person.setName("Empresa persistência fiscal " + suffix);
            person.setCpfCnpj(String.format("%014d", suffix));

            Company company = new Company(person);
            entityManager.persist(company);

            FiscalEstablishment establishment = new FiscalEstablishment(company);
            establishment.setMunicipalityIbgeCode("3105608");
            establishment.setTaxRegime(TaxRegime.SIMPLES_NACIONAL);
            establishment.setEnvironment(FiscalEnvironment.HOMOLOGACAO);
            establishment.setSeries(1);
            establishment.setNextNumber(124L);
            establishmentRepository.saveAndFlush(establishment);

            User user = new User(
                "fiscal_" + suffix,
                "Usuário Fiscal",
                "fiscal_" + suffix + "@example.com",
                "senha-teste",
                "ROLE_USER"
            );
            entityManager.persist(user);

            Sale sale = new Sale();
            sale.setFiscalEstablishment(establishment);
            sale.setUser(user);
            sale.setStatus(SaleStatus.AGUARDANDO_FISCAL);
            sale.setSaleAt(Instant.now());
            sale.setSubtotal(new BigDecimal("10.0000"));
            sale.setDiscount(BigDecimal.ZERO.setScale(4));
            sale.setTotal(new BigDecimal("10.0000"));
            saleRepository.saveAndFlush(sale);

            FiscalDocument document = new FiscalDocument();
            document.setSale(sale);
            document.setEstablishment(establishment);
            document.setSeries(1);
            document.setNumber(123L);
            document.setEmissionType(FiscalEmissionType.NORMAL);
            document.setStatus(FiscalDocumentStatus.AGUARDANDO_AUTORIZACAO);
            document.setReceiptNumber("RECIBO-ANTIGO");
            fiscalDocumentRepository.saveAndFlush(document);

            return new Fixture(sale.getId(), establishment.getId());
        });
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            try {
                return work.call();
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private record Fixture(Long saleId, Long establishmentId) {}

    private record PersistedState(
        FiscalDocumentStatus documentStatus,
        SaleStatus saleStatus,
        String accessKey,
        String xml,
        String responseXml,
        String protocol,
        String receiptNumber,
        String reason,
        Instant issuedAt
    ) {}
}
