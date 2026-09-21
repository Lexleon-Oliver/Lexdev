package net.ddns.lexdev.systempro_api.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.config.CpfCnpjNormalizer;
import net.ddns.lexdev.systempro_api.domain.BankDetails;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.Supplier;
import net.ddns.lexdev.systempro_api.domain.SupplierContact;
import net.ddns.lexdev.systempro_api.domain.SupplierDocument;
import net.ddns.lexdev.systempro_api.dto.SupplierRequestDto;
import net.ddns.lexdev.systempro_api.dto.SupplierResponseDto;
import net.ddns.lexdev.systempro_api.enums.TipoContaBancaria;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.SupplierRepository;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final PersonService personService;
    private final PersonContactService personContactService;
    private final PersonAddressService personAddressService;

    public SupplierService(
        SupplierRepository supplierRepository,
        PersonService personService,
        PersonContactService personContactService,
        PersonAddressService personAddressService
    ) {
        this.supplierRepository = supplierRepository;
        this.personService = personService;
        this.personContactService = personContactService;
        this.personAddressService = personAddressService;
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponseDto> findAll(Pageable pageable) {

        return supplierRepository.findAllWithPerson(pageable)
            .map(SupplierResponseDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public SupplierResponseDto findById(Long id) {

        Supplier supplier = supplierRepository.findByIdWithPerson(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Fornecedor não encontrado com o ID: " + id
            ));

        return SupplierResponseDto.fromEntity(supplier);
    }

    @Transactional
    public SupplierResponseDto create(SupplierRequestDto dto) {

        String cpfCnpj = CpfCnpjNormalizer.normalize(
            dto.person().cpfCnpj()
        );

        if (supplierRepository.existsByPersonCpfCnpj(cpfCnpj)) {
            throw new BusinessException(
                "Esta pessoa/empresa já está cadastrada como fornecedor ativo."
            );
        }

        Person person = personService.getOrCreateForRegistration(
            cpfCnpj
        );

        personService.updateFromDto(
            person,
            dto.person(),
            dto.individual(),
            dto.legalEntity()
        );

        personContactService.updateContacts(
            person,
            dto.contacts()
        );

        personAddressService.updateAddresses(
            person,
            dto.addresses()
        );

        Supplier supplier = new Supplier();
        supplier.setPerson(person);

        copyDtoToSupplier(
            dto,
            supplier
        );

        if (dto.active() != null) {
            supplier.setActive(dto.active());
        }

        return SupplierResponseDto.fromEntity(
            supplierRepository.save(supplier)
        );
    }

    @Transactional
    public SupplierResponseDto update(
        Long id,
        SupplierRequestDto dto
    ) {

        Supplier supplier = supplierRepository.findByIdWithPerson(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Fornecedor não encontrado com o ID: " + id
            ));

        Person person = supplier.getPerson();

        personService.updateFromDto(
            person,
            dto.person(),
            dto.individual(),
            dto.legalEntity()
        );

        personContactService.updateContacts(
            person,
            dto.contacts()
        );

        personAddressService.updateAddresses(
            person,
            dto.addresses()
        );

        copyDtoToSupplier(
            dto,
            supplier
        );

        if (dto.active() != null) {
            supplier.setActive(dto.active());
        }

        return SupplierResponseDto.fromEntity(
            supplierRepository.save(supplier)
        );
    }

    @Transactional
    public void delete(Long id) {

        Supplier supplier = supplierRepository.findByIdWithPerson(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Fornecedor não encontrado com o ID: " + id
            ));

        supplier.setActive(false);

        supplierRepository.save(supplier);
    }

    private void copyDtoToSupplier(
        SupplierRequestDto dto,
        Supplier supplier
    ) {

        supplier.setCondicaoPagamentoPadrao(
            dto.condicaoPagamentoPadrao()
        );

        supplier.setPrazoEntregaDias(
            dto.prazoEntregaDias()
        );

        supplier.setValorMinimoPedido(
            dto.valorMinimoPedido()
        );

        supplier.setCategoria(
            dto.categoria()
        );

        supplier.setObservacoesComerciais(
            dto.observacoesComerciais()
        );

        if (dto.bankDetails() != null) {

            BankDetails bank = new BankDetails(
                dto.bankDetails().banco(),
                dto.bankDetails().agencia(),
                dto.bankDetails().conta(),
                parseTipoConta(
                    dto.bankDetails().tipoConta()
                ),
                dto.bankDetails().chavePix()
            );

            supplier.setBankDetails(bank);
        }

        if (dto.contatos() != null) {

            supplier.getContatos().clear();

            dto.contatos().forEach(c ->
                supplier.getContatos().add(
                    new SupplierContact(
                        c.nome(),
                        c.cargo(),
                        c.email(),
                        c.telefone(),
                        c.setor()
                    )
                )
            );
        }

        if (dto.documentos() != null) {

            supplier.getDocumentos().clear();

            dto.documentos().forEach(d ->
                supplier.getDocumentos().add(
                    new SupplierDocument(
                        d.tipoDocumento(),
                        d.numeroOuUrl(),
                        d.dataValidade()
                    )
                )
            );
        }
    }

    private TipoContaBancaria parseTipoConta(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return TipoContaBancaria.valueOf(
                value.trim().toUpperCase()
            );
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                "Tipo de conta bancária inválido: " + value
            );
        }
    }
}