package net.ddns.lexdev.systempro_api.mapper;

import org.springframework.stereotype.Component;

import net.ddns.lexdev.systempro_api.domain.LegalEntity;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.dto.LegalEntityRequestDto;

@Component
public class LegalEntityMapper {

    public LegalEntity toEntity(
        LegalEntityRequestDto dto,
        Person person
    ) {
        LegalEntity entity = new LegalEntity();
        entity.setPerson(person);
        updateEntity(entity, dto);
        return entity;
    }

    public void updateEntity(
        LegalEntity entity,
        LegalEntityRequestDto dto
    ) {
        entity.setNomeFantasia(dto.nomeFantasia());
        entity.setInscricaoEstadual(dto.inscricaoEstadual());
    }
}
