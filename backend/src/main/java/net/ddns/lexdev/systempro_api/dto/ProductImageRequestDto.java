package net.ddns.lexdev.systempro_api.dto;

import jakarta.validation.constraints.NotBlank;

public record ProductImageRequestDto(

    @NotBlank
    String fileName,

    @NotBlank
    String storagePath,

    String contentType,

    Boolean mainImage,

    Integer sortOrder

) {}
