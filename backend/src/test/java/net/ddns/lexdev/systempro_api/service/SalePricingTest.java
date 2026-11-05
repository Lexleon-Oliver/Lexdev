package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import net.ddns.lexdev.systempro_api.exception.BusinessException;

class SalePricingTest {

    @Test
    void deveCalcularPesosEmQuilosSemPerderCentavos() {
        BigDecimal price = SalePricing.unitPrice(new BigDecimal("10.60"));
        assertThat(SalePricing.gross(SalePricing.quantity("KG", new BigDecimal("0.125")), price))
            .isEqualByComparingTo("1.33");
        assertThat(SalePricing.gross(SalePricing.quantity("KG", new BigDecimal("0.350")),
            new BigDecimal("40.00"))).isEqualByComparingTo("14.00");
        assertThat(SalePricing.gross(SalePricing.quantity("KG", new BigDecimal("1.250")),
            new BigDecimal("40.00"))).isEqualByComparingTo("50.00");
        assertThat(SalePricing.gross(SalePricing.quantity("KG", new BigDecimal("0.001")),
            new BigDecimal("5.00"))).isEqualByComparingTo("0.01");
    }

    @Test
    void devePermitirPrecoUnitarioDeQuatroCasasESomenteCentavosParaPagamentos() {
        assertThat(SalePricing.unitPrice(new BigDecimal("10.1234"))).isEqualByComparingTo("10.1234");
        assertThatThrownBy(() -> SalePricing.amount(new BigDecimal("1.325"), "Pagamento"))
            .isInstanceOf(BusinessException.class).hasMessageContaining("centavos");
        assertThatThrownBy(() -> SalePricing.unitPrice(new BigDecimal("10.12345")))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveRecusarGramasFracionariasNaVendaPorQuilo() {
        assertThatThrownBy(() -> SalePricing.quantity("KG", new BigDecimal("0.0001")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("3 casas").hasMessageContaining("KG");
        assertThatThrownBy(() -> SalePricing.quantity("UN", new BigDecimal("1.5")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("inteira");
        assertThatThrownBy(() -> SalePricing.quantity("CX", new BigDecimal("0.25")))
            .isInstanceOf(BusinessException.class);
        assertThat(SalePricing.quantity("L", new BigDecimal("0.1254"))).isEqualByComparingTo("0.1254");
    }
}
