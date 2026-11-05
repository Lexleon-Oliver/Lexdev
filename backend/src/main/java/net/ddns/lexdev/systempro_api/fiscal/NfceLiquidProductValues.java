package net.ddns.lexdev.systempro_api.fiscal;

import java.math.BigDecimal;

import net.ddns.lexdev.systempro_api.exception.FiscalIntegrationException;

/**
 * Valores SEM tributos da NT 2026.008 v1.01. Não representam preço de venda,
 * desconto comercial ou valor já calculado pelo PDV. O produtor desses dados
 * deve ser um cálculo tributário fiscal auditável; não há preenchimento implícito.
 */
public record NfceLiquidProductValues(BigDecimal unitWithoutTaxes, BigDecimal totalWithoutTaxes) {

    public void validateQuantity(BigDecimal quantity) {
        requireScaleAndRange(unitWithoutTaxes, 10, 11, "vUnComLiq");
        requireScaleAndRange(totalWithoutTaxes, 2, 13, "vProdLiq");
        if (quantity == null || quantity.signum() <= 0
            || quantity.stripTrailingZeros().scale() > 4) {
            throw new FiscalIntegrationException("Quantidade fiscal inválida para cálculo do valor líquido.");
        }
        BigDecimal expected = unitWithoutTaxes.multiply(quantity);
        if (expected.subtract(totalWithoutTaxes).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new FiscalIntegrationException(
                "vProdLiq difere de vUnComLiq x qCom acima da tolerância fiscal de R$ 0,01."
            );
        }
    }

    private static void requireScaleAndRange(
        BigDecimal value, int maxDecimals, int maxIntegers, String field
    ) {
        if (value == null || value.signum() < 0) {
            throw new FiscalIntegrationException("Valor fiscal negativo ou ausente em " + field + ".");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > maxDecimals
            || normalized.precision() - normalized.scale() > maxIntegers) {
            throw new FiscalIntegrationException(
                "Valor fiscal fora da precisão permitida no XML: " + field + "."
            );
        }
    }
}
