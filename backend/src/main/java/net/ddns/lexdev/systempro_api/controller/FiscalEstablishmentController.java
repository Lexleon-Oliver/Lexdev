package net.ddns.lexdev.systempro_api.controller;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import net.ddns.lexdev.systempro_api.dto.FiscalEstablishmentRequestDto;
import net.ddns.lexdev.systempro_api.dto.FiscalEstablishmentResponseDto;
import net.ddns.lexdev.systempro_api.dto.FiscalServiceStatusDto;
import net.ddns.lexdev.systempro_api.fiscal.NfceIssueResult;
import net.ddns.lexdev.systempro_api.fiscal.SefazNfceGateway;
import net.ddns.lexdev.systempro_api.service.FiscalEstablishmentService;


@RestController
@RequestMapping("/fiscal/establishments")
public class FiscalEstablishmentController {
    private final FiscalEstablishmentService service;
    private final SefazNfceGateway gateway;

    public FiscalEstablishmentController(FiscalEstablishmentService service, SefazNfceGateway gateway) { this.service = service; this.gateway = gateway; }

    @GetMapping
    public List<FiscalEstablishmentResponseDto> findAll() { return service.findAll(); }

    @PostMapping
    public ResponseEntity<FiscalEstablishmentResponseDto> create(@Valid @RequestBody FiscalEstablishmentRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public FiscalEstablishmentResponseDto update(@PathVariable Long id, @Valid @RequestBody FiscalEstablishmentRequestDto dto) { return service.update(id, dto); }

    @PostMapping(value = "/{id}/certificate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadCertificate(@PathVariable Long id, @RequestPart("file") MultipartFile file, @RequestParam("password") String password) {
        service.uploadCertificate(id, file, password);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/status")
    public FiscalServiceStatusDto status(@PathVariable Long id) {
        NfceIssueResult result = gateway.status(service.requireDetailed(id));
        String code = extractStatusCode(result.responseXml());
        return new FiscalServiceStatusDto(code, result.reason(), result.status(), Instant.now());
    }

    private String extractStatusCode(String xml) {
        if (xml == null || xml.isBlank()) return null;
        try {
            var f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            var d = f.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
            var n = d.getElementsByTagNameNS("*", "cStat").item(0);
            return n == null ? null : n.getTextContent();
        } catch (Exception ignored) {
            return null;
        }
    }
}
