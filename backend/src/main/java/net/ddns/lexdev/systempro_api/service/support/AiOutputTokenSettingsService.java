package net.ddns.lexdev.systempro_api.service.support;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.ddns.lexdev.systempro_api.config.AiProperties;
import net.ddns.lexdev.systempro_api.domain.OperationalSetting;
import net.ddns.lexdev.systempro_api.domain.OperationalSettingAudit;
import net.ddns.lexdev.systempro_api.dto.support.AiOutputTokensDto;
import net.ddns.lexdev.systempro_api.repository.OperationalSettingRepository;
import net.ddns.lexdev.systempro_api.repository.OperationalSettingAuditRepository;

/**
 * Única chave operacional editável na Fase 2. Nunca atualiza Environment ou .properties.
 * A leitura pelo gateway é feita por requisição, sem cache de processo.
 */
@Service
public class AiOutputTokenSettingsService {
    public static final String KEY = "ai.max-output-tokens";
    private final AiProperties properties;
    private final OperationalSettingRepository repository;
    private final OperationalSettingAuditRepository audit;
    private final Clock clock;

    @Autowired
    public AiOutputTokenSettingsService(AiProperties properties,
                                        OperationalSettingRepository repository,
                                        OperationalSettingAuditRepository audit) {
        this(properties, repository, audit, Clock.systemUTC());
    }

    AiOutputTokenSettingsService(AiProperties properties,
                                 OperationalSettingRepository repository,
                                 OperationalSettingAuditRepository audit,
                                 Clock clock) {
        this.properties = properties;
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public int effectiveValue() {
        return repository.findById(KEY)
            .map(OperationalSetting::getIntValue)
            .orElseGet(this::installationDefault);
    }

    @Transactional(readOnly = true)
    public AiOutputTokensDto current() {
        return repository.findById(KEY)
            .map(item -> new AiOutputTokensDto(item.getIntValue(), installationDefault(), true))
            .orElseGet(() -> new AiOutputTokensDto(installationDefault(), installationDefault(), false));
    }

    @Transactional
    public AiOutputTokensDto update(Integer requested, String username) {
        if (requested == null || requested < 128 || requested > 2048) {
            throw new IllegalArgumentException("O limite da IA deve estar entre 128 e 2048 tokens.");
        }
        String actor = validatedActor(username);
        var existing = repository.findById(KEY);
        if (existing.isPresent() && existing.get().getIntValue() == requested) {
            return current();
        }
        Instant at = clock.instant();
        Integer old = existing.map(OperationalSetting::getIntValue).orElse(null);
        OperationalSetting setting = existing.orElseGet(
            () -> new OperationalSetting(KEY, requested, actor, at));
        setting.update(requested, actor, at);
        repository.saveAndFlush(setting);
        audit.save(new OperationalSettingAudit(KEY, old, requested,
            old != null ? old : installationDefault(), requested, actor, at));
        return new AiOutputTokensDto(requested, installationDefault(), true);
    }

    @Transactional
    public AiOutputTokensDto reset(String username) {
        String actor = validatedActor(username);
        var existing = repository.findById(KEY);
        if (existing.isPresent()) {
            Integer old = existing.get().getIntValue();
            repository.delete(existing.get());
            repository.flush();
            audit.save(new OperationalSettingAudit(KEY, old, null,
                old, installationDefault(), actor, clock.instant()));
        }
        return new AiOutputTokensDto(installationDefault(), installationDefault(), false);
    }

    private int installationDefault() {
        int configured = properties.maxOutputTokens();
        return configured > 0 ? configured : 600;
    }

    private static String validatedActor(String actor) {
        if (actor == null || actor.isBlank() || actor.length() > 50) {
            throw new IllegalArgumentException("Identificação do suporte inválida.");
        }
        return actor;
    }
}
