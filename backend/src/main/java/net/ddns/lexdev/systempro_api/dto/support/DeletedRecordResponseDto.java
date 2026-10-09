package net.ddns.lexdev.systempro_api.dto.support;
import java.time.Instant;
public record DeletedRecordResponseDto(Long id, DeletedRecordType type, String title, String subtitle, Instant lastModifiedAt) {}