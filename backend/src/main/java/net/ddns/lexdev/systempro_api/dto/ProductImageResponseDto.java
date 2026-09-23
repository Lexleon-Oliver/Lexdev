package net.ddns.lexdev.systempro_api.dto;

public record ProductImageResponseDto(
    Long id,
    String fileName,
    String contentType,
    Long fileSize,
    Boolean mainImage,
    Integer sortOrder,
    String url
) {}
