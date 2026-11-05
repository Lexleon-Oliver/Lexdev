package net.ddns.lexdev.systempro_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * O PL_010f permanece padrão. A emissão de valores líquidos da NT 2026.008
 * exige opt-in explícito e snapshots tributários completos em todos os itens.
 */
@ConfigurationProperties(prefix = "systempro.fiscal.schema")
public record NfceSchemaProperties(String location, boolean emitLiquidValues) {

    /** Vincula explicitamente o construtor canônico na presença da sobrecarga legada. */
    @ConstructorBinding
    public NfceSchemaProperties { }

    /** Compatibilidade com os testes e usos do construtor anterior. */
    public NfceSchemaProperties(String location) {
        this(location, false);
    }
}
