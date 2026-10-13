package net.ddns.lexdev.systempro_api.controller.support;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.ddns.lexdev.systempro_api.dto.support.DeletedRecordPageResponseDto;
import net.ddns.lexdev.systempro_api.dto.support.DeletedRecordType;
import net.ddns.lexdev.systempro_api.service.support.SoftDeleteRecoveryService;

@RestController
@RequestMapping("/support/deleted-records")
public class SoftDeleteRecoveryController {

    private final SoftDeleteRecoveryService service;

    public SoftDeleteRecoveryController(SoftDeleteRecoveryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<DeletedRecordPageResponseDto> findDeleted(
        @RequestParam DeletedRecordType type,
        @RequestParam(defaultValue = "") String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(service.findDeleted(type, search, page, size));
    }

    @PatchMapping("/{type}/{id}/restore")
    public ResponseEntity<Void> restore(
        @PathVariable DeletedRecordType type,
        @PathVariable Long id
    ) {
        service.restore(type, id);
        return ResponseEntity.noContent().build();
    }
}