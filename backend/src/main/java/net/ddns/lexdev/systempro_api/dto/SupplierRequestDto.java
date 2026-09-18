package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SupplierRequestDto(

    @NotNull
    @Valid
    PersonRequestDto person,

    @Valid
    IndividualPersonRequestDto individual,

    @Valid
    LegalEntityRequestDto legalEntity,

    @Valid
    List<PersonContactRequestDto> contacts,

    @Valid
    List<PersonAddressRequestDto> addresses,

    String condicaoPagamentoPadrao,

    Integer prazoEntregaDias,

    BigDecimal valorMinimoPedido,

    String categoria,

    String observacoesComerciais,

    BankDetailsDto bankDetails,

    List<SupplierContactDto> contatos,

    List<SupplierDocumentDto> documentos,

    Boolean active

) {}