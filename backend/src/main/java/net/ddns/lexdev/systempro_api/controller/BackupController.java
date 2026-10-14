package net.ddns.lexdev.systempro_api.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.ddns.lexdev.systempro_api.dto.backup.BackupResponseDto;
import net.ddns.lexdev.systempro_api.service.backup.BackupService;

@RestController
@RequestMapping("/backups")
public class BackupController {

    private static final MediaType BACKUP_MEDIA_TYPE = MediaType.parseMediaType("application/octet-stream");

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @PostMapping
    public ResponseEntity<BackupResponseDto> generate() {
        return ResponseEntity.ok(backupService.generate());
    }

    @GetMapping
    public ResponseEntity<List<BackupResponseDto>> list() {
        return ResponseEntity.ok(backupService.list());
    }

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        Resource resource = backupService.load(fileName);
        ContentDisposition disposition = ContentDisposition.attachment()
            .filename(fileName, java.nio.charset.StandardCharsets.UTF_8)
            .build();

        return ResponseEntity.ok()
            .contentType(BACKUP_MEDIA_TYPE)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("X-Content-Type-Options", "nosniff")
            .body(resource);
    }
}