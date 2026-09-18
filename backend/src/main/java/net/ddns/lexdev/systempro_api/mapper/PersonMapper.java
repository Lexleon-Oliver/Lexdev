package net.ddns.lexdev.systempro_api.mapper;

import org.springframework.stereotype.Component;

import net.ddns.lexdev.systempro_api.config.CpfCnpjNormalizer;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.domain.TipoPessoaParser;
import net.ddns.lexdev.systempro_api.dto.PersonRequestDto;

@Component
public class PersonMapper {

    public Person toEntity(PersonRequestDto dto) {
        Person person = new Person();
        updateEntity(person, dto);
        return person;
    }

    public void updateEntity(
        Person person,
        PersonRequestDto dto
    ) {
        person.setTipoPessoa(
            TipoPessoaParser.parse(dto.tipoPessoa())
        );

        person.setName(dto.name());

        person.setCpfCnpj(
            CpfCnpjNormalizer.normalize(dto.cpfCnpj())
        );
    }
}