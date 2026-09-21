package net.ddns.lexdev.systempro_api.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.PersonAddress;
import net.ddns.lexdev.systempro_api.dto.PersonAddressRequestDto;
import net.ddns.lexdev.systempro_api.enums.AddressType;
import net.ddns.lexdev.systempro_api.exception.BusinessException;

@Service
public class PersonAddressService {

    /**
     * Atualiza os endereços de uma Person.
     *
     * null: preserva os endereços existentes.
     * lista vazia: remove todos os endereços.
     * lista preenchida: substitui os endereços existentes.
     */
    public void updateAddresses(
        Person person,
        List<PersonAddressRequestDto> addressDtos
    ) {

        if (addressDtos == null) {
            return;
        }

        // Remove os endereços atuais mantendo
        // o relacionamento bidirecional consistente.
        for (PersonAddress address :
                new ArrayList<>(person.getAddresses())) {

            person.removeAddress(address);
        }

        // Lista vazia significa remover todos.
        if (addressDtos.isEmpty()) {
            return;
        }

        for (PersonAddressRequestDto dto : addressDtos) {

            PersonAddress address = new PersonAddress();

            address.setType(parseAddressType(dto.type()));
            address.setCep(dto.cep());
            address.setLogradouro(dto.logradouro());
            address.setNumero(dto.numero());
            address.setComplemento(dto.complemento());
            address.setBairro(dto.bairro());
            address.setCidade(dto.cidade());
            address.setUf(dto.uf());
            address.setPrincipal(
                Boolean.TRUE.equals(dto.principal())
            );

            person.addAddress(address);
        }
    }

    private AddressType parseAddressType(String value) {

        if (value == null || value.isBlank()) {
            throw new BusinessException(
                "O tipo de endereço deve ser informado."
            );
        }

        try {
            return AddressType.valueOf(
                value.trim().toUpperCase()
            );

        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                "Tipo de endereço inválido: " + value
            );
        }
    }
}
