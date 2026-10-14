package net.ddns.lexdev.systempro_api.dto.backup;

import java.time.Instant;

public record BackupResponseDto(
    String fileName,
    long sizeBytes,
    Instant createdAt
) {}