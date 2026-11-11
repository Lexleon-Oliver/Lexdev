package net.ddns.lexdev.systempro_api.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import net.ddns.lexdev.systempro_api.dto.support.ConfigurationOverviewDto;

class ConfigurationOverviewServiceTest {

    @Test
    void flagsRefletemSomenteValoresExplicitamentePermitidos() {
        MockEnvironment env = new MockEnvironment()
            .withProperty("systempro.fiscal.enabled", "true")
            .withProperty("systempro.fiscal.production-ready", "false")
            .withProperty("systempro.fiscal.schema.emit-liquid-values", "false")
            .withProperty("systempro.fiscal.liquid-zero-tax-producer-enabled", "false")
            .withProperty("systempro.ai.enabled", "true")
            .withProperty("systempro.backup.scheduling.enabled", "false");

        ConfigurationOverviewDto result = new ConfigurationOverviewService(env).overview();

        assertThat(result.features()).hasSize(6);
        assertThat(result.features()).extracting(ConfigurationOverviewDto.FeatureStatus::key)
            .doesNotHaveDuplicates();
        assertThat(result.features()).filteredOn(f -> f.key().equals("systempro.fiscal.enabled"))
            .singleElement().satisfies(f -> assertThat(f.enabled()).isTrue());
        assertThat(result.features()).filteredOn(f -> f.key().equals("systempro.ai.enabled"))
            .singleElement().satisfies(f -> assertThat(f.enabled()).isTrue());
        assertThat(result.features()).filteredOn(f -> f.category().equals("Fiscal experimental"))
            .allSatisfy(f -> assertThat(f.enabled()).isFalse());
        assertThat(result.features()).allSatisfy(f -> assertThat(f.requiresRestart()).isTrue());
    }

    @Test
    void nuncaRetornaCredenciaisNemOutrasPropriedadesArbitrarias() {
        String secret = "valor-secreto-sentinela-NAO-EXPOR";
        MockEnvironment env = new MockEnvironment()
            .withProperty("jwt.secret.access", secret)
            .withProperty("systempro.storage.secret-key", secret)
            .withProperty("systempro.ai.openai.api-key", secret)
            .withProperty("systempro.license.enforcement-enabled", "true")
            .withProperty("systempro.fiscal.encryption-key", secret);

        ConfigurationOverviewDto result = new ConfigurationOverviewService(env).overview();

        assertThat(result.toString()).doesNotContain(secret, "jwt.secret", "api-key", "encryption-key", "secret-key");
        assertThat(result.features()).extracting(ConfigurationOverviewDto.FeatureStatus::key)
            .doesNotContain("systempro.license.enforcement-enabled");
        assertThat(result.features()).allSatisfy(f -> assertThat(f.enabled()).isFalse());
    }

    @Test
    void ausenciaDeFlagsNaoHabilitaRecursos() {
        ConfigurationOverviewDto result = new ConfigurationOverviewService(new MockEnvironment()).overview();
        assertThat(result.features()).allSatisfy(f -> assertThat(f.enabled()).isFalse());
    }
}
