package net.ddns.lexdev.systempro_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardInsightResponseDto;
import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardResponseDto;
import net.ddns.lexdev.systempro_api.service.DashboardInsightService;
import net.ddns.lexdev.systempro_api.service.DashboardService;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService service;
    private final DashboardInsightService insightService;

    public DashboardController(
        DashboardService service,
        DashboardInsightService insightService
    ) {
        this.service = service;
        this.insightService = insightService;
    }

    @GetMapping
    public ResponseEntity<DashboardResponseDto> dashboard() {
        return ResponseEntity.ok(service.dashboard());
    }

    @GetMapping("/insights")
    public ResponseEntity<DashboardInsightResponseDto> insights() {
        return ResponseEntity.ok(insightService.insight());
    }
}
