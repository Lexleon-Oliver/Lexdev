package net.ddns.lexdev.systempro_api.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import net.ddns.lexdev.systempro_api.config.AiProperties;
import net.ddns.lexdev.systempro_api.domain.OperationalSetting;
import net.ddns.lexdev.systempro_api.domain.OperationalSettingAudit;
import net.ddns.lexdev.systempro_api.repository.OperationalSettingRepository;
import net.ddns.lexdev.systempro_api.repository.OperationalSettingAuditRepository;

class AiOutputTokenSettingsServiceTest {
    private final AiProperties props = mock(AiProperties.class);
    private final OperationalSettingRepository repo = mock(OperationalSettingRepository.class);
    private final OperationalSettingAuditRepository audit = mock(OperationalSettingAuditRepository.class);
    private final Instant now = Instant.parse("2026-11-11T12:00:00Z");
    private AiOutputTokenSettingsService service;

    @BeforeEach
    void setUp() {
        when(props.maxOutputTokens()).thenReturn(600);
        service = new AiOutputTokenSettingsService(props, repo, audit, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void ausenciaDeOverrideUsaConfiguracaoExternaSemPersistir() {
        when(repo.findById(AiOutputTokenSettingsService.KEY)).thenReturn(Optional.empty());
        assertThat(service.effectiveValue()).isEqualTo(600);
        assertThat(service.current().installationDefault()).isEqualTo(600);
        assertThat(service.current().overridden()).isFalse();
        verify(repo, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void salvarCriaOverrideComTrilhaDeAuditoria() {
        when(repo.findById(AiOutputTokenSettingsService.KEY)).thenReturn(Optional.empty());
        var dto = service.update(900, "support");
        assertThat(dto.effectiveValue()).isEqualTo(900);
        assertThat(dto.installationDefault()).isEqualTo(600);
        assertThat(dto.overridden()).isTrue();
        ArgumentCaptor<OperationalSetting> setting = ArgumentCaptor.forClass(OperationalSetting.class);
        verify(repo).saveAndFlush(setting.capture());
        assertThat(setting.getValue().getKey()).isEqualTo("ai.max-output-tokens");
        assertThat(setting.getValue().getIntValue()).isEqualTo(900);
        assertThat(setting.getValue().getUpdatedBy()).isEqualTo("support");
        ArgumentCaptor<OperationalSettingAudit> entry = ArgumentCaptor.forClass(OperationalSettingAudit.class);
        verify(audit).save(entry.capture());
        assertThat(entry.getValue().getOldValue()).isNull();
        assertThat(entry.getValue().getNewValue()).isEqualTo(900);
        assertThat(entry.getValue().getOldEffectiveValue()).isEqualTo(600);
        assertThat(entry.getValue().getNewEffectiveValue()).isEqualTo(900);
        assertThat(entry.getValue().getOccurredAt()).isEqualTo(now);
    }

    @Test
    void atualizacaoPreservaAnteriorENaoAuditaValorIgual() {
        var stored = new OperationalSetting(AiOutputTokenSettingsService.KEY, 800, "anterior", now);
        when(repo.findById(AiOutputTokenSettingsService.KEY)).thenReturn(Optional.of(stored));
        service.update(950, "support");
        var entry = ArgumentCaptor.forClass(OperationalSettingAudit.class);
        verify(audit).save(entry.capture());
        assertThat(entry.getValue().getOldValue()).isEqualTo(800);
        assertThat(entry.getValue().getNewValue()).isEqualTo(950);
        service.update(950, "support");
        verify(audit, times(1)).save(any());
    }

    @Test
    void restaurarRemoveSomenteOverrideEAudita() {
        var stored = new OperationalSetting(AiOutputTokenSettingsService.KEY, 950, "support", now);
        when(repo.findById(AiOutputTokenSettingsService.KEY)).thenReturn(Optional.of(stored));
        var dto = service.reset("support");
        verify(repo).delete(stored);
        verify(repo).flush();
        var entry = ArgumentCaptor.forClass(OperationalSettingAudit.class);
        verify(audit).save(entry.capture());
        assertThat(entry.getValue().getOldValue()).isEqualTo(950);
        assertThat(entry.getValue().getNewValue()).isNull();
        assertThat(entry.getValue().getOldEffectiveValue()).isEqualTo(950);
        assertThat(entry.getValue().getNewEffectiveValue()).isEqualTo(600);
        assertThat(dto.effectiveValue()).isEqualTo(600);
        assertThat(dto.overridden()).isFalse();
    }

    @Test
    void nuncaPersisteValorInvalidoOuAtorInvalido() {
        for (Integer value : new Integer[] {null, -1, 0, 127, 2049}) {
            assertThatThrownBy(() -> service.update(value, "support"))
                .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.update(600, " "))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repo, audit);
    }
}
