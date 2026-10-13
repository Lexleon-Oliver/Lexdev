package net.ddns.lexdev.systempro_api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import net.ddns.lexdev.systempro_api.domain.StockSettings;
import net.ddns.lexdev.systempro_api.dto.stock.StockSettingsDto;
import net.ddns.lexdev.systempro_api.repository.StockSettingsRepository;

@Service
public class StockSettingsService {
    private final StockSettingsRepository repository;

    public StockSettingsService(StockSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public StockSettingsDto find() {
        StockSettings settings = repository.findById(StockSettings.SINGLETON_ID)
            .orElseThrow(() -> new IllegalStateException("Configuração global de estoque não encontrada."));
        return new StockSettingsDto(settings.getMinSellableShelfLifeDays());
    }

    @Transactional(readOnly = true)
    public int minSellableShelfLifeDays() {
        return find().minSellableShelfLifeDays();
    }

    @Transactional
    public StockSettingsDto save(StockSettingsDto dto) {
        StockSettings settings = repository.findById(StockSettings.SINGLETON_ID)
            .orElseThrow(() -> new IllegalStateException("Configuração global de estoque não encontrada."));
        settings.setMinSellableShelfLifeDays(dto.minSellableShelfLifeDays());
        repository.save(settings);
        return new StockSettingsDto(settings.getMinSellableShelfLifeDays());
    }
}