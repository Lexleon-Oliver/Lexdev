package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;
import java.util.List;

import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.Supplier;

// Response DTO
public record SupplierResponseDto(
    Long id,
    PersonResponseDto person,
    IndividualPersonResponseDto individual,
    LegalEntityResponseDto legalEntity,
    List<PersonContactResponseDto> contacts,
    List<PersonAddressResponseDto> addresses,
    String condicaoPagamentoPadrao,
    Integer prazoEntregaDias,
    BigDecimal valorMinimoPedido,
    String categoria,
    String observacoesComerciais,
    BankDetailsDto bankDetails,
    List<SupplierContactDto> contatos,
    List<SupplierDocumentDto> documentos,
    Boolean active
) {

    public static SupplierResponseDto fromEntity(
        Supplier supplier
    ) {
        Person person = supplier.getPerson();

        BankDetailsDto bankDto =
            supplier.getBankDetails() != null
                ? new BankDetailsDto(
                    supplier.getBankDetails().getBanco(),
                    supplier.getBankDetails().getAgencia(),
                    supplier.getBankDetails().getConta(),
                    supplier.getBankDetails().getTipoConta() != null
                        ? supplier.getBankDetails()
                            .getTipoConta()
                            .name()
                        : null,
                    supplier.getBankDetails().getChavePix()
                )
                : null;

        List<SupplierContactDto> supplierContacts =
            supplier.getContatos()
                .stream()
                .map(c -> new SupplierContactDto(
                    c.getNome(),
                    c.getCargo(),
                    c.getEmail(),
                    c.getTelefone(),
                    c.getSetor()
                ))
                .toList();

        List<SupplierDocumentDto> documents =
            supplier.getDocumentos()
                .stream()
                .map(d -> new SupplierDocumentDto(
                    d.getTipoDocumento(),
                    d.getNumeroOuUrl(),
                    d.getDataValidade()
                ))
                .toList();

        List<PersonContactResponseDto> contacts =
            person.getContacts()
                .stream()
                .map(PersonContactResponseDto::fromEntity)
                .toList();

        List<PersonAddressResponseDto> addresses =
            person.getAddresses()
                .stream()
                .map(PersonAddressResponseDto::fromEntity)
                .toList();

        return new SupplierResponseDto(
            supplier.getId(),
            PersonResponseDto.fromEntity(person),
            IndividualPersonResponseDto.fromEntity(
                person.getIndividualPerson()
            ),
            LegalEntityResponseDto.fromEntity(
                person.getLegalEntity()
            ),
            contacts,
            addresses,
            supplier.getCondicaoPagamentoPadrao(),
            supplier.getPrazoEntregaDias(),
            supplier.getValorMinimoPedido(),
            supplier.getCategoria(),
            supplier.getObservacoesComerciais(),
            bankDto,
            supplierContacts,
            documents,
            supplier.isActive()
        );
    }
}