package net.ddns.lexdev.systempro_api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import net.ddns.lexdev.systempro_api.config.JwtAuthenticationEntryPoint;
import net.ddns.lexdev.systempro_api.config.JwtAuthenticationFilter;
import net.ddns.lexdev.systempro_api.config.JwtTokenProvider;
import net.ddns.lexdev.systempro_api.config.SecurityConfig;
import net.ddns.lexdev.systempro_api.dto.backup.BackupResponseDto;
import net.ddns.lexdev.systempro_api.repository.UserRepository;
import net.ddns.lexdev.systempro_api.service.backup.BackupService;

@WebMvcTest(BackupController.class)
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    JwtAuthenticationEntryPoint.class
})
class BackupSecurityTest {
    private static final String FILE_NAME = "systempro-backup-20261014T020000Z.spbackup";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BackupService backupService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @CsvSource({"ADMIN,200", "SUPPORT,200", "MANAGER,403", "USER,403"})
    void deveRestringirGeracaoPorRole(String role, int expectedStatus) throws Exception {
        when(backupService.generate()).thenReturn(
            new BackupResponseDto(FILE_NAME, 100, Instant.parse("2026-10-14T02:00:00Z"))
        );

        mockMvc.perform(post("/backups").with(user("tester").roles(role)))
            .andExpect(status().is(expectedStatus));
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,200", "SUPPORT,200", "MANAGER,403", "USER,403"})
    void deveRestringirListagemPorRole(String role, int expectedStatus) throws Exception {
        when(backupService.list()).thenReturn(List.of());

        mockMvc.perform(get("/backups").with(user("tester").roles(role)))
            .andExpect(status().is(expectedStatus));
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,200", "SUPPORT,200", "MANAGER,403", "USER,403"})
    void deveRestringirDownloadPorRole(String role, int expectedStatus) throws Exception {
        Path file = Files.writeString(tempDir.resolve(FILE_NAME), "backup");
        when(backupService.load(FILE_NAME)).thenReturn(new FileSystemResource(file));

        mockMvc.perform(get("/backups/{fileName}", FILE_NAME).with(user("tester").roles(role)))
            .andExpect(status().is(expectedStatus));
    }
}