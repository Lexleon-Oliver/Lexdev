package net.ddns.lexdev.systempro_api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProductImageUpdateDto(

    @NotNull
    Boolean mainImage,

    @NotNull
    @Min(0)
    Integer sortOrder

) {
}
