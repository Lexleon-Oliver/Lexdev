package net.ddns.lexdev.systempro_api.dto;

public record ProductImageResponseDto(

    Long id,

    String fileName,

    String storagePath,

    String contentType,

    Boolean mainImage,

    Integer sortOrder

) {}
