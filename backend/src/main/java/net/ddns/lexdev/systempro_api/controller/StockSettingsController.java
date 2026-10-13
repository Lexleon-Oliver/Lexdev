package net.ddns.lexdev.systempro_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import net.ddns.lexdev.systempro_api.dto.stock.StockSettingsDto;
import net.ddns.lexdev.systempro_api.service.StockSettingsService;

@RestController
@RequestMapping("/stock/settings")
public class StockSettingsController {
    private final StockSettingsService service;

    public StockSettingsController(StockSettingsService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<StockSettingsDto> find() { return ResponseEntity.ok(service.find()); }

    @PutMapping
    public ResponseEntity<StockSettingsDto> save(@Valid @RequestBody StockSettingsDto dto) {
        return ResponseEntity.ok(service.save(dto));
    }
}