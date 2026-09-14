package net.ddns.lexdev.systempro_api.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.domain.BankDetails;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.Supplier;
import net.ddns.lexdev.systempro_api.domain.SupplierContact;
import net.ddns.lexdev.systempro_api.domain.SupplierDocument;
import net.ddns.lexdev.systempro_api.dto.SupplierRequestDto;
import net.ddns.lexdev.systempro_api.dto.SupplierResponseDto;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.PersonRepository;
import net.ddns.lexdev.systempro_api.repository.SupplierRepository;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final PersonRepository personRepository;

    public SupplierService(SupplierRepository supplierRepository, PersonRepository personRepository) {
        this.supplierRepository = supplierRepository;
        this.personRepository = personRepository;
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponseDto> findAll(Pageable pageable) {
        return supplierRepository.findAllWithPerson(pageable).map(SupplierResponseDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public SupplierResponseDto findById(Long id) {
        Supplier supplier = supplierRepository.findByIdWithPerson(id)
                .orElseThrow(() -> new EntityNotFoundException("Fornecedor não encontrado com o ID: " + id));
        return SupplierResponseDto.fromEntity(supplier);
    }

    @Transactional
    public SupplierResponseDto create(SupplierRequestDto dto) {
        String cleanCpfCnpj = sanitize(dto.cpfCnpj());

        if (supplierRepository.existsByPersonCpfCnpj(cleanCpfCnpj)) {
            throw new BusinessException("Esta pessoa/empresa já está cadastrada como fornecedor ativo.");
        }

        // Busca Person existente pelo CPF/CNPJ ou instancia uma nova
        Person person = personRepository.findByCpfCnpj(cleanCpfCnpj)
                .orElseGet(Person::new);

        copyDtoToPerson(dto, person);
        person.setCpfCnpj(cleanCpfCnpj);

        Supplier supplier = new Supplier();
        supplier.setPerson(person);
        copyDtoToSupplier(dto, supplier);

        return SupplierResponseDto.fromEntity(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponseDto update(Long id, SupplierRequestDto dto) {
        Supplier supplier = supplierRepository.findByIdWithPerson(id)
                .orElseThrow(() -> new EntityNotFoundException("Fornecedor não encontrado com o ID: " + id));

        String cleanCpfCnpj = sanitize(dto.cpfCnpj());
        Person person = supplier.getPerson();

        personRepository.findByCpfCnpj(cleanCpfCnpj).ifPresent(existingPerson -> {
            if (!existingPerson.getId().equals(person.getId())) {
                throw new BusinessException("CPF/CNPJ já cadastrado para outra pessoa no sistema.");
            }
        });

        copyDtoToPerson(dto, person);
        person.setCpfCnpj(cleanCpfCnpj);
        copyDtoToSupplier(dto, supplier);

        return SupplierResponseDto.fromEntity(supplierRepository.save(supplier));
    }

    @Transactional
    public void delete(Long id) {
        Supplier supplier = supplierRepository.findByIdWithPerson(id)
                .orElseThrow(() -> new EntityNotFoundException("Fornecedor não encontrado com o ID: " + id));

        supplier.setActive(false);
        supplierRepository.save(supplier);
    }

    private String sanitize(String value) {
        return value != null ? value.replaceAll("\\D", "") : null;
    }

    private void copyDtoToPerson(SupplierRequestDto dto, Person person) {
        person.setTipoPessoa(dto.tipoPessoa());
        person.setName(dto.name());
        person.setNomeFantasia(dto.nomeFantasia());
        person.setRgIe(dto.rgIe());
        person.setEmail(dto.email());
        person.setPhone(dto.phone());
        person.setCep(dto.cep());
        person.setLogradouro(dto.logradouro());
        person.setNumero(dto.numero());
        person.setComplemento(dto.complemento());
        person.setBairro(dto.bairro());
        person.setCidade(dto.cidade());
        person.setUf(dto.uf());
        if (dto.active() != null) {
            person.setActive(dto.active());
        }
    }

    private void copyDtoToSupplier(SupplierRequestDto dto, Supplier supplier) {
        supplier.setCondicaoPagamentoPadrao(dto.condicaoPagamentoPadrao());
        supplier.setPrazoEntregaDias(dto.prazoEntregaDias());
        supplier.setValorMinimoPedido(dto.valorMinimoPedido());
        supplier.setCategoria(dto.categoria());
        supplier.setObservacoesComerciais(dto.observacoesComerciais());

        if (dto.bankDetails() != null) {
            BankDetails bank = new BankDetails(
                dto.bankDetails().banco(),
                dto.bankDetails().agencia(),
                dto.bankDetails().conta(),
                dto.bankDetails().tipoConta(),
                dto.bankDetails().chavePix()
            );
            supplier.setBankDetails(bank);
        }

        if (dto.contatos() != null) {
            supplier.getContatos().clear();
            dto.contatos().forEach(c -> supplier.getContatos().add(
                new SupplierContact(c.nome(), c.cargo(), c.email(), c.telefone(), c.setor())
            ));
        }

        if (dto.documentos() != null) {
            supplier.getDocumentos().clear();
            dto.documentos().forEach(d -> supplier.getDocumentos().add(
                new SupplierDocument(d.tipoDocumento(), d.numeroOuUrl(), d.dataValidade())
            ));
        }
    }
}
