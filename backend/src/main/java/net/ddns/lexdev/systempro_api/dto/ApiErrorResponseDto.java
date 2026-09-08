package net.ddns.lexdev.systempro_api.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponseDto(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<FieldErrorDetails> fieldErrors
) {
    public record FieldErrorDetails(String field, String message) {}
}
