package net.ddns.lexdev.systempro_api.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import net.ddns.lexdev.systempro_api.dto.SaleCreateRequestDto;
import net.ddns.lexdev.systempro_api.dto.SaleResponseDto;
import net.ddns.lexdev.systempro_api.service.SaleService;
import net.ddns.lexdev.systempro_api.fiscal.danfe.NfceDanfeService;

@RestController
@RequestMapping("/sales")
public class SaleController {
    private final SaleService service;
    private final NfceDanfeService danfeService;
    public SaleController(SaleService service, NfceDanfeService danfeService) {
        this.service = service;
        this.danfeService = danfeService;
    }

    @PostMapping
    public ResponseEntity<SaleResponseDto> create(@Valid @RequestBody SaleCreateRequestDto dto) {
        return ResponseEntity.status(201).body(service.create(dto));
    }

    @PostMapping("/{id}/issue")
    public ResponseEntity<SaleResponseDto> issue(@PathVariable Long id) {
        return ResponseEntity.ok(service.issue(id));
    }

    @PostMapping("/{id}/contingency/offline")
    public ResponseEntity<SaleResponseDto> enterOfflineContingency(
        @PathVariable Long id,
        @RequestParam String justification
    ) {
        return ResponseEntity.ok(service.enterOfflineContingency(id, justification));
    }

    @PostMapping("/{id}/contingency/transmit")
    public ResponseEntity<SaleResponseDto> transmitOfflineContingency(@PathVariable Long id) {
        return ResponseEntity.ok(service.transmitOfflineContingency(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SaleResponseDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    public Page<SaleResponseDto> findAll(@PageableDefault(size = 20, sort = "saleAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.findAll(pageable);
    }

    @PostMapping("/{id}/consult")
    public ResponseEntity<SaleResponseDto> consult(@PathVariable Long id) {
        return ResponseEntity.ok(service.consult(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<SaleResponseDto> cancel(@PathVariable Long id, @RequestParam String justification) {
        return ResponseEntity.ok(service.cancel(id, justification));
    }

    @GetMapping("/{id}/fiscal/danfe")
    public ResponseEntity<byte[]> downloadDanfe(@PathVariable Long id) {
        byte[] body = danfeService.generate(id);
        String filename = "danfe-nfce-venda-%d.pdf".formatted(id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(body.length)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
            .body(body);
    }

    @GetMapping("/{id}/fiscal/xml")
    public ResponseEntity<byte[]> downloadXml(@PathVariable Long id) {
        String xml = service.requireFiscalXml(id);
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        String filename = "nfce-venda-%d.xml".formatted(id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_XML)
            .contentLength(body.length)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
            .body(body);
    }
}