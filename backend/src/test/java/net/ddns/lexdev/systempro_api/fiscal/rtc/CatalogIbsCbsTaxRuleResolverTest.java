package net.ddns.lexdev.systempro_api.fiscal.rtc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.domain.IbsCbsTaxClassification;
import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;
import net.ddns.lexdev.systempro_api.repository.IbsCbsTaxClassificationRepository;

class CatalogIbsCbsTaxRuleResolverTest {
    private IbsCbsTaxClassificationRepository repository;
    private IbsCbsRateProvider rateProvider;
    private CatalogIbsCbsTaxRuleResolver resolver;
    private final LocalDate date = LocalDate.of(2026, 10, 8);

    @BeforeEach
    void setUp() {
        repository = mock(IbsCbsTaxClassificationRepository.class);
        rateProvider = mock(IbsCbsRateProvider.class);
        resolver = new CatalogIbsCbsTaxRuleResolver(repository, rateProvider);
    }

    @Test
    void resolvesNfceRuleFromCatalogAndSeparateRates() {
        var classification = classification(true, BigDecimal.ZERO, BigDecimal.ZERO);
        when(repository.findApplicable("000", "000001", date)).thenReturn(Optional.of(classification));
        when(rateProvider.resolve(date)).thenReturn(new IbsCbsRates(
            new BigDecimal("0.1000"), BigDecimal.ZERO, new BigDecimal("0.9000")));

        IbsCbsTaxRule rule = resolver.resolve("000", "000001", date, 65);

        assertTrue(rule.nfceAllowed());
        assertEquals(new BigDecimal("0.1000"), rule.ibsUfRate());
        assertEquals(BigDecimal.ZERO, rule.ibsMunicipalRate());
        assertEquals(new BigDecimal("0.9000"), rule.cbsRate());
    }

    @Test
    void appliesCatalogReductionWithoutChangingNominalRateProvider() {
        var classification = classification(true, new BigDecimal("60.0000"), new BigDecimal("60.0000"));
        when(repository.findApplicable("000", "000001", date)).thenReturn(Optional.of(classification));
        when(rateProvider.resolve(date)).thenReturn(new IbsCbsRates(
            new BigDecimal("0.1000"), BigDecimal.ZERO, new BigDecimal("0.9000")));

        IbsCbsTaxRule rule = resolver.resolve("000", "000001", date, 65);

        assertEquals(0, new BigDecimal("0.0400").compareTo(rule.ibsUfRate()));
        assertEquals(0, new BigDecimal("0.3600").compareTo(rule.cbsRate()));
    }

    @Test
    void preservesNfceRestrictionFromOfficialCatalogSnapshot() {
        var classification = classification(false, BigDecimal.ZERO, BigDecimal.ZERO);
        when(repository.findApplicable("000", "000001", date)).thenReturn(Optional.of(classification));
        when(rateProvider.resolve(date)).thenReturn(new IbsCbsRates(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        IbsCbsTaxRule rule = resolver.resolve("000", "000001", date, 65);
        assertFalse(rule.nfceAllowed());
    }

    @Test
    void rejectsUnknownOrOutOfValidityClassificationBeforeRates() {
        when(repository.findApplicable("000", "000999", date)).thenReturn(Optional.empty());

        assertThrows(FiscalConfigurationException.class,
            () -> resolver.resolve("000", "000999", date, 65));
        verifyNoInteractions(rateProvider);
    }

    private IbsCbsTaxClassification classification(boolean nfceAllowed, BigDecimal ibsReduction, BigDecimal cbsReduction) {
        return new IbsCbsTaxClassification(
            "000001", "000", "Teste", IbsCbsTaxationMode.REGULAR, nfceAllowed,
            ibsReduction, cbsReduction, LocalDate.of(2026, 1, 1), null, "IT 2025.002 v1.70"
        );
    }
}
