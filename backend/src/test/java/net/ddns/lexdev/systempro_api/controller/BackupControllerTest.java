package net.ddns.lexdev.systempro_api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import net.ddns.lexdev.systempro_api.dto.backup.BackupResponseDto;
import net.ddns.lexdev.systempro_api.service.backup.BackupService;

class BackupControllerTest {

    private BackupService backupService;
    private MockMvc mockMvc;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        backupService = mock(BackupService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new BackupController(backupService)).build();
    }

    @Test
    void deveGerarBackup() throws Exception {
        when(backupService.generate()).thenReturn(
            new BackupResponseDto("systempro-backup-20261014T020000Z.spbackup", 100, Instant.now())
        );

        mockMvc.perform(post("/backups"))
            .andExpect(status().isOk());
    }

    @Test
    void deveListarBackups() throws Exception {
        when(backupService.list()).thenReturn(List.of());

        mockMvc.perform(get("/backups"))
            .andExpect(status().isOk());
    }

    @Test
    void deveFazerDownloadComoAnexoSemCache() throws Exception {
        String fileName = "systempro-backup-20261014T020000Z.spbackup";
        Path file = Files.writeString(tempDir.resolve(fileName), "backup");
        when(backupService.load(fileName)).thenReturn(new FileSystemResource(file));

        mockMvc.perform(get("/backups/{fileName}", fileName))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                org.hamcrest.Matchers.containsString("attachment")));
    }
}