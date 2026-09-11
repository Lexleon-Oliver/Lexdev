package net.ddns.lexdev.systempro_api.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.domain.Client;
import net.ddns.lexdev.systempro_api.dto.ClientRequestDto;
import net.ddns.lexdev.systempro_api.dto.ClientResponseDto;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.ClientRepository;

@Service
public class ClientService {

    private final ClientRepository repository;

    public ClientService(ClientRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<ClientResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(ClientResponseDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public ClientResponseDto findById(Long id) {
        Client client = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado com o ID: " + id));
        return ClientResponseDto.fromEntity(client);
    }

    @Transactional
    public ClientResponseDto create(ClientRequestDto dto) {
        String cleanCpfCnpj = sanitize(dto.cpfCnpj());

        if (repository.existsByCpfCnpj(cleanCpfCnpj)) {
            throw new BusinessException("Já existe um cliente ativo cadastrado com este CPF/CNPJ.");
        }

        Client client = new Client();
        copyDtoToEntity(dto, client);
        client.setCpfCnpj(cleanCpfCnpj); // Garante que o valor limpo seja salvo
        
        return ClientResponseDto.fromEntity(repository.save(client));
    }

    @Transactional
    public ClientResponseDto update(Long id, ClientRequestDto dto) {
        Client client = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado com o ID: " + id));

        String cleanCpfCnpj = sanitize(dto.cpfCnpj());

        repository.findByCpfCnpj(cleanCpfCnpj).ifPresent(existingClient -> {
            if (!existingClient.getId().equals(id)) {
                throw new BusinessException("CPF/CNPJ já cadastrado para outro cliente.");
            }
        });

        copyDtoToEntity(dto, client);
        client.setCpfCnpj(cleanCpfCnpj);
        
        return ClientResponseDto.fromEntity(repository.save(client));
    }

    @Transactional
    public void delete(Long id) {
        Client client = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado com o ID: " + id));

        client.setActive(false);
        repository.save(client);
    }

    private String sanitize(String value) {
        return value != null ? value.replaceAll("\\D", "") : null;
    }

    private void copyDtoToEntity(ClientRequestDto dto, Client entity) {
        entity.setTipoPessoa(dto.tipoPessoa());
        entity.setName(dto.name());
        entity.setNomeFantasia(dto.nomeFantasia());
        entity.setRgIe(dto.rgIe());
        entity.setEmail(dto.email());
        entity.setPhone(dto.phone());
        entity.setCep(dto.cep());
        entity.setLogradouro(dto.logradouro());
        entity.setNumero(dto.numero());
        entity.setComplemento(dto.complemento());
        entity.setBairro(dto.bairro());
        entity.setCidade(dto.cidade());
        entity.setUf(dto.uf());
        if (dto.active() != null) {
            entity.setActive(dto.active());
        }
    }
}