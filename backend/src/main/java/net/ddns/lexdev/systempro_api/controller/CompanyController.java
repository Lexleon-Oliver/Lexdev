package net.ddns.lexdev.systempro_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import net.ddns.lexdev.systempro_api.dto.CompanyRequestDto;
import net.ddns.lexdev.systempro_api.dto.CompanyResponseDto;
import net.ddns.lexdev.systempro_api.service.CompanyService;

@RestController
@RequestMapping("/company")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public ResponseEntity<CompanyResponseDto> find() {
        return ResponseEntity.ok(companyService.find());
    }

    @PutMapping
    public ResponseEntity<CompanyResponseDto> save(@Valid @RequestBody CompanyRequestDto dto) {
        return ResponseEntity.ok(companyService.save(dto));
    }
}
