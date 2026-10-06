package net.ddns.lexdev.systempro_api.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.ddns.lexdev.systempro_api.dto.PersonOptionResponseDto;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.repository.PersonRepository;
@RestController
@RequestMapping("/persons")
public class PersonController {

    private final PersonRepository repository;

    public PersonController(PersonRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/legal-entities")
    public ResponseEntity<Page<PersonOptionResponseDto>> legalEntities(
        @RequestParam(defaultValue = "") String q,
        @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        String raw = q == null ? "" : q.trim();
        String digits = raw.replaceAll("\\D", "");
        String search = digits.length() == raw.length() ? digits : raw;

        return ResponseEntity.ok(
            repository.searchByType(TipoPessoa.PJ, search, pageable)
                .map(PersonOptionResponseDto::fromEntity)
        );
    }
}