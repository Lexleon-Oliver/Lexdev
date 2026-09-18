package net.ddns.lexdev.systempro_api.domain;

import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.exception.BusinessException;

public final class TipoPessoaParser {

    private TipoPessoaParser() {
    }

    public static TipoPessoa parse(String value) {

        if (value == null || value.isBlank()) {
            throw new BusinessException(
                "Tipo de pessoa é obrigatório."
            );
        }

        try {
            return TipoPessoa.valueOf(
                value.trim().toUpperCase()
            );

        } catch (IllegalArgumentException e) {

            throw new BusinessException(
                "Tipo de pessoa inválido: " + value
            );
        }
    }
}
