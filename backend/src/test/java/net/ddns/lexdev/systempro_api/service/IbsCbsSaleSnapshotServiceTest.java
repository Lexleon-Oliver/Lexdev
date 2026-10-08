package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.FiscalProductProfile;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;
import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsTaxRule;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsTaxRuleResolver;
import net.ddns.lexdev.systempro_api.fiscal.rtc.IbsCbsTaxationMode;

@ExtendWith(MockitoExtension.class)
class IbsCbsSaleSnapshotServiceTest {

    @Mock private IbsCbsTaxRuleResolver resolver;
    private IbsCbsSaleSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new IbsCbsSaleSnapshotService(resolver);
    }

    @Test
    void regimeNormalDeveResolverCalcularEPreservarSnapshotSobreTotalLiquidoDoItem() {
        FiscalEstablishment establishment = new FiscalEstablishment(null);
        establishment.setTaxRegime(TaxRegime.REGIME_NORMAL);
        FiscalProductProfile profile = profile("000", "000001");
        SaleItem item = new SaleItem();
        item.setTotal(new BigDecimal("90.00"));
        LocalDate date = LocalDate.of(2026, 10, 8);

        IbsCbsTaxRule rule = new IbsCbsTaxRule(
            "000", "000001", IbsCbsTaxationMode.REGULAR, true,
            new BigDecimal("0.100000"), BigDecimal.ZERO, new BigDecimal("0.900000")
        );
        when(resolver.resolve("000", "000001", date, 65)).thenReturn(rule);

        service.apply(establishment, profile, item, date);

        assertThat(item.getIbsCbsTaxBase()).isEqualByComparingTo("90.00");
        assertThat(item.getIbsUfAmount()).isEqualByComparingTo("0.09");
        assertThat(item.getIbsMunicipalAmount()).isEqualByComparingTo("0.00");
        assertThat(item.getIbsTotalAmount()).isEqualByComparingTo("0.09");
        assertThat(item.getCbsAmount()).isEqualByComparingTo("0.81");
        assertThat(item.getIbsCbsCstSnapshot()).isEqualTo("000");
        assertThat(item.getCClassTribSnapshot()).isEqualTo("000001");
    }

    @Test
    void regimeNormalSemClassificacaoDeveFalharAntesDePersistirSnapshot() {
        FiscalEstablishment establishment = new FiscalEstablishment(null);
        establishment.setTaxRegime(TaxRegime.REGIME_NORMAL);
        SaleItem item = new SaleItem();
        item.setTotal(new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.apply(
            establishment, profile(null, null), item, LocalDate.of(2026, 10, 8)
        )).isInstanceOf(FiscalConfigurationException.class)
          .hasMessageContaining("CST IBS/CBS e cClassTrib");

        verify(resolver, never()).resolve(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        assertThat(item.getIbsCbsCalculation()).isNull();
    }

    @Test
    void simplesNacionalNaoDeveCriarSnapshotRtcEm2026() {
        FiscalEstablishment establishment = new FiscalEstablishment(null);
        establishment.setTaxRegime(TaxRegime.SIMPLES_NACIONAL);
        SaleItem item = new SaleItem();
        item.setTotal(new BigDecimal("100.00"));

        service.apply(establishment, profile("000", "000001"), item, LocalDate.of(2026, 10, 8));

        assertThat(item.getIbsCbsCalculation()).isNull();
        verify(resolver, never()).resolve(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private static FiscalProductProfile profile(String cst, String cClassTrib) {
        FiscalProductProfile profile = new FiscalProductProfile(null);
        profile.setIbsCbsCst(cst);
        profile.setCClassTrib(cClassTrib);
        return profile;
    }
}