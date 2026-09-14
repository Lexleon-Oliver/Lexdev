package net.ddns.lexdev.systempro_api.dto;

// DTOs auxiliares
public record BankDetailsDto(
    String banco,
    String agencia,
    String conta,
    String tipoConta,
    String chavePix
) {}
