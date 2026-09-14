package net.ddns.lexdev.systempro_api.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.domain.Client;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.dto.ClientRequestDto;
import net.ddns.lexdev.systempro_api.dto.ClientResponseDto;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.ClientRepository;
import net.ddns.lexdev.systempro_api.repository.PersonRepository;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final PersonRepository personRepository;

    public ClientService(ClientRepository clientRepository, PersonRepository personRepository) {
        this.clientRepository = clientRepository;
        this.personRepository = personRepository;
    }

    @Transactional(readOnly = true)
    public Page<ClientResponseDto> findAll(Pageable pageable) {
        return clientRepository.findAllWithPerson(pageable).map(ClientResponseDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public ClientResponseDto findById(Long id) {
        Client client = clientRepository.findByIdWithPerson(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado com o ID: " + id));
        return ClientResponseDto.fromEntity(client);
    }

    @Transactional
    public ClientResponseDto create(ClientRequestDto dto) {
        String cleanCpfCnpj = sanitize(dto.cpfCnpj());

        if (clientRepository.existsByPersonCpfCnpj(cleanCpfCnpj)) {
            throw new BusinessException("Esta pessoa/empresa já está cadastrada como cliente ativo.");
        }

        Person person = personRepository.findByCpfCnpj(cleanCpfCnpj)
                .orElseGet(Person::new);

        copyDtoToPerson(dto, person);
        person.setCpfCnpj(cleanCpfCnpj);

        Client client = new Client();
        client.setPerson(person);

        return ClientResponseDto.fromEntity(clientRepository.save(client));
    }

    @Transactional
    public ClientResponseDto update(Long id, ClientRequestDto dto) {
        Client client = clientRepository.findByIdWithPerson(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado com o ID: " + id));

        String cleanCpfCnpj = sanitize(dto.cpfCnpj());
        Person person = client.getPerson();

        personRepository.findByCpfCnpj(cleanCpfCnpj).ifPresent(existingPerson -> {
            if (!existingPerson.getId().equals(person.getId())) {
                throw new BusinessException("CPF/CNPJ já cadastrado para outra pessoa.");
            }
        });

        copyDtoToPerson(dto, person);
        person.setCpfCnpj(cleanCpfCnpj);

        return ClientResponseDto.fromEntity(clientRepository.save(client));
    }

    @Transactional
    public void delete(Long id) {
        Client client = clientRepository.findByIdWithPerson(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado com o ID: " + id));

        client.setActive(false);
        clientRepository.save(client);
    }

    private String sanitize(String value) {
        return value != null ? value.replaceAll("\\D", "") : null;
    }

    private void copyDtoToPerson(ClientRequestDto dto, Person person) {
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
}