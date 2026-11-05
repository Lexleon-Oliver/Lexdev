package net.ddns.lexdev.systempro_api.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

import net.ddns.lexdev.systempro_api.exception.BusinessException;

/** Regras únicas de precisão para venda, estoque por unidade e valores fiscais. */
final class SalePricing {
    private static final Set<String> INDIVISIBLE = Set.of("UN", "CX", "PC", "PAR", "RL", "FD");

    private SalePricing() {}

    static BigDecimal unitPrice(BigDecimal price) {
        if (price == null) {
            throw new BusinessException("O preço unitário é obrigatório.");
        }
        try {
            // Preço por KG pode ter frações de centavo; valor cobrado do item não.
            return price.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new BusinessException("O preço unitário permite no máximo quatro casas decimais.", e);
        }
    }

    static BigDecimal amount(BigDecimal value, String field) {
        if (value == null) return BigDecimal.ZERO.setScale(2);
        if (value.signum() < 0) {
            throw new BusinessException(field + " não pode ser negativo.");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new BusinessException(field + " deve ser informado em centavos (duas casas decimais).", e);
        }
    }

    static BigDecimal quantity(String unitOfMeasure, BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new BusinessException("A quantidade vendida deve ser maior que zero.");
        }
        String unit = unitOfMeasure == null ? "" : unitOfMeasure.trim().toUpperCase(Locale.ROOT);
        int maxScale = INDIVISIBLE.contains(unit) ? 0 : ("KG".equals(unit) ? 3 : 4);
        try {
            return value.setScale(maxScale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new BusinessException(INDIVISIBLE.contains(unit)
                ? "A unidade " + unit + " exige quantidade inteira."
                : "A unidade " + unit + " aceita no máximo " + maxScale + " casas decimais.", e);
        }
    }

    static BigDecimal gross(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }
}
