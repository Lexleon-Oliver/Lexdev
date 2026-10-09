package net.ddns.lexdev.systempro_api.controller.support;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import net.ddns.lexdev.systempro_api.dto.support.*;
import net.ddns.lexdev.systempro_api.service.support.SoftDeleteRecoveryService;
@RestController
@RequestMapping("/support/deleted-records")
public class SoftDeleteRecoveryController {
    private final SoftDeleteRecoveryService service;
    public SoftDeleteRecoveryController(SoftDeleteRecoveryService service) { this.service=service; }
    @GetMapping public ResponseEntity<List<DeletedRecordResponseDto>> findDeleted(@RequestParam DeletedRecordType type,@RequestParam(defaultValue="") String search) { return ResponseEntity.ok(service.findDeleted(type,search)); }
    @PatchMapping("/{type}/{id}/restore") public ResponseEntity<Void> restore(@PathVariable DeletedRecordType type,@PathVariable Long id) { service.restore(type,id); return ResponseEntity.noContent().build(); }
}