package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;

public record ProductSupplierResponseDto(

    Long id,

    Long supplierId,

    String supplierCode,

    BigDecimal purchasePrice,

    Integer leadTimeDays,

    BigDecimal minimumOrderQuantity,

    Boolean preferred

) {}
