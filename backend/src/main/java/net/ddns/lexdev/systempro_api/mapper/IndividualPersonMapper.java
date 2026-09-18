package net.ddns.lexdev.systempro_api.mapper;


import org.springframework.stereotype.Component;

import net.ddns.lexdev.systempro_api.domain.IndividualPerson;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.dto.IndividualPersonRequestDto;

@Component
public class IndividualPersonMapper {

    public IndividualPerson toEntity(
        IndividualPersonRequestDto dto,
        Person person
    ) {
        IndividualPerson entity = new IndividualPerson();
        entity.setPerson(person);
        updateEntity(entity, dto);
        return entity;
    }

    public void updateEntity(
        IndividualPerson entity,
        IndividualPersonRequestDto dto
    ) {
        entity.setRg(dto.rg());
    }
}