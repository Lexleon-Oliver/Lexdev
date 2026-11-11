package net.ddns.lexdev.systempro_api.controller.support;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.ddns.lexdev.systempro_api.dto.support.ConfigurationOverviewDto;
import net.ddns.lexdev.systempro_api.service.support.ConfigurationOverviewService;

/** Protegido pelo matcher /support/** -> ROLE_SUPPORT em SecurityConfig. */
@RestController
@RequestMapping("/support/configuration")
public class ConfigurationOverviewController {

    private final ConfigurationOverviewService service;

    public ConfigurationOverviewController(ConfigurationOverviewService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ConfigurationOverviewDto> overview() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(service.overview());
    }
}
