package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record ProductSupplierRequestDto(

    @NotNull
    Long supplierId,

    String supplierCode,

    @DecimalMin(value = "0.0")
    BigDecimal purchasePrice,

    Integer leadTimeDays,

    @DecimalMin(value = "0.0")
    BigDecimal minimumOrderQuantity,

    Boolean preferred

) {}
