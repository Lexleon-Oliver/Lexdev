package net.ddns.lexdev.systempro_api.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import net.ddns.lexdev.systempro_api.domain.Company;
import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.enums.FiscalEnvironment;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.integration.IntegrationTestBase;

class FiscalNumberingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private FiscalEstablishmentRepository establishmentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deveSerializarIncrementosConcorrentesComPessimisticWrite() throws Exception {
        Long establishmentId = createEstablishment(700L);
        CountDownLatch firstLockAcquired = new CountDownLatch(1);

        CompletableFuture<Long> first = CompletableFuture.supplyAsync(() ->
            inTransaction(() -> {
                FiscalEstablishment establishment = establishmentRepository.findByIdForUpdate(establishmentId).orElseThrow();
                long reserved = establishment.getNextNumber();
                firstLockAcquired.countDown();
                sleep(300);
                establishment.setNextNumber(reserved + 1);
                return reserved;
            })
        );

        assertThat(firstLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Long> second = CompletableFuture.supplyAsync(() ->
            inTransaction(() -> {
                FiscalEstablishment establishment = establishmentRepository.findByIdForUpdate(establishmentId).orElseThrow();
                long reserved = establishment.getNextNumber();
                establishment.setNextNumber(reserved + 1);
                return reserved;
            })
        );

        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
            .containsExactly(700L, 701L);
        assertThat(readNextNumber(establishmentId)).isEqualTo(702L);
    }

    @Test
    void deveRestaurarNextNumberQuandoTransacaoFalhar() {
        Long establishmentId = createEstablishment(800L);

        assertThatThrownBy(() ->
            inTransaction(() -> {
                FiscalEstablishment establishment = establishmentRepository.findByIdForUpdate(establishmentId).orElseThrow();
                establishment.setNextNumber(establishment.getNextNumber() + 1);
                establishmentRepository.flush();
                throw new IllegalStateException("falha proposital para validar rollback");
            })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(readNextNumber(establishmentId)).isEqualTo(800L);
    }

    @Test
    void deveExistirConstraintUnicaParaEstabelecimentoSerieENumero() {
        String definition = jdbcTemplate.queryForObject(
            """
            SELECT pg_get_constraintdef(c.oid)
              FROM pg_constraint c
              JOIN pg_class t ON t.oid = c.conrelid
             WHERE t.relname = 'tb_fiscal_document'
               AND c.conname = 'uk_fiscal_document_number'
            """,
            String.class
        );

        assertThat(definition)
            .isNotNull()
            .containsIgnoringCase("UNIQUE")
            .contains("establishment_id")
            .contains("series")
            .contains("number");
    }

    private Long createEstablishment(long nextNumber) {
        return inTransaction(() -> {
            Person person = new Person();
            person.setTipoPessoa(TipoPessoa.PJ);
            person.setName("Empresa teste fiscal " + System.nanoTime());
            person.setCpfCnpj(nextCnpj());

            Company company = new Company(person);
            entityManager.persist(company);

            FiscalEstablishment establishment = new FiscalEstablishment(company);
            establishment.setMunicipalityIbgeCode("3105608");
            establishment.setTaxRegime(TaxRegime.SIMPLES_NACIONAL);
            establishment.setEnvironment(FiscalEnvironment.HOMOLOGACAO);
            establishment.setSeries(1);
            establishment.setNextNumber(nextNumber);
            establishmentRepository.saveAndFlush(establishment);
            return establishment.getId();
        });
    }

    private long readNextNumber(Long establishmentId) {
        return inTransaction(() -> establishmentRepository.findById(establishmentId).orElseThrow().getNextNumber());
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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static String nextCnpj() {
        long value = Math.floorMod(System.nanoTime(), 100_000_000_000_000L);
        return String.format("%014d", value);
    }
}
