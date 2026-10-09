package net.ddns.lexdev.systempro_api.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.ddns.lexdev.systempro_api.dto.report.FinancialReportResponseDto;
import net.ddns.lexdev.systempro_api.dto.report.StockReportResponseDto;
import net.ddns.lexdev.systempro_api.service.ReportService;

@RestController
@RequestMapping("/reports")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service = service; }

    @GetMapping("/stock")
    public ResponseEntity<StockReportResponseDto> stock(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) { return ResponseEntity.ok(service.stock(startDate, endDate)); }

    @GetMapping("/financial")
    public ResponseEntity<FinancialReportResponseDto> financial(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) { return ResponseEntity.ok(service.financial(startDate, endDate)); }
}