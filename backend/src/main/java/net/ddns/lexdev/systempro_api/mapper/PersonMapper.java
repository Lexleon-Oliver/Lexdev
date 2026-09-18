package net.ddns.lexdev.systempro_api.mapper;

import org.springframework.stereotype.Component;

import net.ddns.lexdev.systempro_api.config.CpfCnpjNormalizer;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.dto.PersonRequestDto;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.exception.BusinessException;

@Component
public class PersonMapper {

    public Person toEntity(PersonRequestDto dto) {
        Person person = new Person();

        updateEntity(person, dto);

        return person;
    }

    public void updateEntity(Person person, PersonRequestDto dto) {

        person.setTipoPessoa(parseTipoPessoa(dto.tipoPessoa()));
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

        person.setCpfCnpj(CpfCnpjNormalizer.normalize(dto.cpfCnpj()));
    }

    private TipoPessoa parseTipoPessoa(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return TipoPessoa.valueOf(
                value.trim().toUpperCase()
            );
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                "Tipo de pessoa inválido: " + value
            );
        }
    }

}
