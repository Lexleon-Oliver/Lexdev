package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;
import java.util.List;

import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.Supplier;

// Response DTO
public record SupplierResponseDto(
    Long id,
    Long personId,
    
    // Dados Person
    String tipoPessoa,
    String name,
    String nomeFantasia,
    String cpfCnpj,
    String rgIe,
    String email,
    String phone,
    String cep,
    String logradouro,
    String numero,
    String complemento,
    String bairro,
    String cidade,
    String uf,

    // Dados Comerciais
    String condicaoPagamentoPadrao,
    Integer prazoEntregaDias,
    BigDecimal valorMinimoPedido,
    String categoria,
    String observacoesComerciais,

    // Agrupamentos
    BankDetailsDto bankDetails,
    List<SupplierContactDto> contatos,
    List<SupplierDocumentDto> documentos,
    Boolean active
) {
    public static SupplierResponseDto fromEntity(Supplier supplier) {
        Person p = supplier.getPerson();

        BankDetailsDto bankDto = supplier.getBankDetails() != null ? new BankDetailsDto(
                supplier.getBankDetails().getBanco(),
                supplier.getBankDetails().getAgencia(),
                supplier.getBankDetails().getConta(),
                supplier.getBankDetails().getTipoConta(),
                supplier.getBankDetails().getChavePix()
        ) : null;

        List<SupplierContactDto> contactsDto = supplier.getContatos().stream()
                .map(c -> new SupplierContactDto(c.getNome(), c.getCargo(), c.getEmail(), c.getTelefone(), c.getSetor()))
                .toList();

        List<SupplierDocumentDto> docsDto = supplier.getDocumentos().stream()
                .map(d -> new SupplierDocumentDto(d.getTipoDocumento(), d.getNumeroOuUrl(), d.getDataValidade()))
                .toList();

        return new SupplierResponseDto(
            supplier.getId(),
            p.getId(),
            p.getTipoPessoa(),
            p.getName(),
            p.getNomeFantasia(),
            p.getCpfCnpj(),
            p.getRgIe(),
            p.getEmail(),
            p.getPhone(),
            p.getCep(),
            p.getLogradouro(),
            p.getNumero(),
            p.getComplemento(),
            p.getBairro(),
            p.getCidade(),
            p.getUf(),
            supplier.getCondicaoPagamentoPadrao(),
            supplier.getPrazoEntregaDias(),
            supplier.getValorMinimoPedido(),
            supplier.getCategoria(),
            supplier.getObservacoesComerciais(),
            bankDto,
            contactsDto,
            docsDto,
            supplier.getActive()
        );
    }
}
