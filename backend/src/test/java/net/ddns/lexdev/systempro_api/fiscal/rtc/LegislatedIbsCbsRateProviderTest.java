package net.ddns.lexdev.systempro_api.fiscal.rtc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;

class LegislatedIbsCbsRateProviderTest {
    private final LegislatedIbsCbsRateProvider provider = new LegislatedIbsCbsRateProvider();

    @Test
    void deveResolverAliquotasPadraoDe2026() {
        IbsCbsRates rates = provider.resolve(LocalDate.of(2026, 10, 8));

        assertThat(rates.ibsUfRate()).isEqualByComparingTo(new BigDecimal("0.1"));
        assertThat(rates.ibsMunicipalRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(rates.cbsRate()).isEqualByComparingTo(new BigDecimal("0.9"));
    }

    @Test
    void naoDeveInventarAliquotasDeOutroExercicio() {
        assertThatThrownBy(() -> provider.resolve(LocalDate.of(2027, 1, 1)))
            .isInstanceOf(FiscalConfigurationException.class)
            .hasMessageContaining("2027")
            .hasMessageContaining("ainda não estão parametrizadas");
    }
}