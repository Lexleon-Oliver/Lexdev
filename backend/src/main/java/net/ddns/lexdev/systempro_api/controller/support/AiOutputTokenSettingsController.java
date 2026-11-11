package net.ddns.lexdev.systempro_api.controller.support;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import net.ddns.lexdev.systempro_api.dto.support.AiOutputTokensDto;
import net.ddns.lexdev.systempro_api.service.support.AiOutputTokenSettingsService;

/** /support/** continua protegido por ROLE_SUPPORT em SecurityConfig. */
@RestController
@RequestMapping("/support/configuration/ai-output-tokens")
public class AiOutputTokenSettingsController {
    private final AiOutputTokenSettingsService service;

    public AiOutputTokenSettingsController(AiOutputTokenSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<AiOutputTokensDto> current() {
        return noCache(service.current());
    }

    @PutMapping
    public ResponseEntity<AiOutputTokensDto> update(@Valid @RequestBody AiOutputTokensDto.Update input,
                                                     Authentication authentication) {
        return noCache(service.update(input.value(), authentication.getName()));
    }

    @DeleteMapping
    public ResponseEntity<AiOutputTokensDto> reset(Authentication authentication) {
        return noCache(service.reset(authentication.getName()));
    }

    private static ResponseEntity<AiOutputTokensDto> noCache(AiOutputTokensDto dto) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dto);
    }
}
