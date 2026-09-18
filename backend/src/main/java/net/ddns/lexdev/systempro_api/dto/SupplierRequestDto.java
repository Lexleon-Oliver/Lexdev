package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;
import java.util.List;


// Request DTO
public record SupplierRequestDto(
    PersonRequestDto person,
    String condicaoPagamentoPadrao,
    Integer prazoEntregaDias,
    BigDecimal valorMinimoPedido,
    String categoria,
    String observacoesComerciais,

    // --- Objetos Aninhados ---
    BankDetailsDto bankDetails,
    List<SupplierContactDto> contatos,
    List<SupplierDocumentDto> documentos,
    Boolean active
) {}
