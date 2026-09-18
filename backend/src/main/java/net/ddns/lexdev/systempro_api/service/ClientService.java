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

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final PersonService personService;

    public ClientService(
        ClientRepository clientRepository,
        PersonService personService
    ) {
        this.clientRepository = clientRepository;
        this.personService = personService;
    }

    @Transactional(readOnly = true)
    public Page<ClientResponseDto> findAll(Pageable pageable) {
        return clientRepository.findAllWithPerson(pageable)
            .map(ClientResponseDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public ClientResponseDto findById(Long id) {
        Client client = clientRepository.findByIdWithPerson(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Cliente não encontrado com o ID: " + id
            ));

        return ClientResponseDto.fromEntity(client);
    }

    @Transactional
    public ClientResponseDto create(ClientRequestDto dto) {
        String cleanCpfCnpj = dto.person().cleanCpfCnpj();

        if (clientRepository.existsByPersonCpfCnpj(cleanCpfCnpj)) {
            throw new BusinessException(
                "Esta pessoa/empresa já está cadastrada como cliente ativo."
            );
        }

        Person person = personService.getOrCreateForRegistration(cleanCpfCnpj);
        personService.copyDtoToPerson(dto.person(), person);

        Client client = new Client();
        client.setPerson(person);

        return ClientResponseDto.fromEntity(clientRepository.save(client));
    }

    @Transactional
    public ClientResponseDto update(Long id, ClientRequestDto dto) {
        Client client = clientRepository.findByIdWithPerson(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Cliente não encontrado com o ID: " + id
            ));

        Person person = client.getPerson();

        personService.ensureCpfCnpjAvailable(
            dto.person().cleanCpfCnpj(),
            person.getId()
        );

        personService.copyDtoToPerson(dto.person(), person);

        if (dto.active() != null) {
            client.setActive(dto.active());
        }

        return ClientResponseDto.fromEntity(clientRepository.save(client));
    }

    @Transactional
    public void delete(Long id) {
        Client client = clientRepository.findByIdWithPerson(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Cliente não encontrado com o ID: " + id
            ));

        client.setActive(false);
        clientRepository.save(client);
    }
}