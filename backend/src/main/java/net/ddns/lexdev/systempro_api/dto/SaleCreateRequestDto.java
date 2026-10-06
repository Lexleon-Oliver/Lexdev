package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaleCreateRequestDto(
    @NotNull Long fiscalEstablishmentId,
    Long clientId,
    @Pattern(regexp = "(?:\\d{11}|\\d{14})", message = "CPF/CNPJ do consumidor deve possuir 11 ou 14 dígitos")
    String consumerCpfCnpj,
    @NotEmpty @Valid List<SaleItemRequestDto> items,
    @NotEmpty @Valid List<SalePaymentRequestDto> payments,
    @DecimalMin("0.0") BigDecimal discount,
    @Size(max = 500) String note
) {}
