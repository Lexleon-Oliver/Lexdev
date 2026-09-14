package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// Request DTO
public record SupplierRequestDto(
    // --- Person (Dados gerais e Endereço principal) ---
    @NotBlank(message = "Tipo de pessoa é obrigatório") String tipoPessoa,
    @NotBlank(message = "Nome/Razão Social é obrigatório") String name,
    String nomeFantasia,
    @NotBlank(message = "CPF/CNPJ é obrigatório") String cpfCnpj,
    String rgIe,
    @NotBlank(message = "E-mail principal é obrigatório") @Email String email,
    @NotBlank(message = "Telefone principal é obrigatório") String phone,
    String cep,
    String logradouro,
    String numero,
    String complemento,
    String bairro,
    String cidade,
    String uf,

    // --- Dados Comerciais ---
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
