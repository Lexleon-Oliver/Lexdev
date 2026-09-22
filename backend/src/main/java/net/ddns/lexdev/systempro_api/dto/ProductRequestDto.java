package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import net.ddns.lexdev.systempro_api.enums.ProductStatus;

public record ProductRequestDto(

    @NotBlank
    @Size(max = 50)
    String code,

    @NotBlank
    @Size(max = 200)
    String name,

    String description,

    @Size(max = 100)
    String model,

    @Size(max = 100)
    String manufacturerCode,

    @Size(max = 14)
    String gtin,

    @NotNull
    ProductStatus status,

    @NotBlank
    @Size(max = 10)
    String unitOfMeasure,

    @NotNull
    Boolean controlsStock,

    @DecimalMin(value = "0.0")
    BigDecimal salePrice,

    @DecimalMin(value = "0.0")
    BigDecimal minimumSalePrice,

    @DecimalMin(value = "0.0")
    BigDecimal minimumStock,

    @DecimalMin(value = "0.0")
    BigDecimal maximumStock,

    @DecimalMin(value = "0.0")
    BigDecimal reorderPoint,

    @Size(max = 8)
    String ncm,

    @Size(max = 7)
    String cest,

    @Size(max = 1)
    String origin,

    @DecimalMin(value = "0.0")
    BigDecimal grossWeight,

    @DecimalMin(value = "0.0")
    BigDecimal netWeight,

    @DecimalMin(value = "0.0")
    BigDecimal height,

    @DecimalMin(value = "0.0")
    BigDecimal width,

    @DecimalMin(value = "0.0")
    BigDecimal length,

    @Valid
    List<ProductSupplierRequestDto> suppliers,

    @Valid
    List<ProductImageRequestDto> images

) {}
