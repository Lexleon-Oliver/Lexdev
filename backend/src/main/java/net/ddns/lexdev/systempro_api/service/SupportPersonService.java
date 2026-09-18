package net.ddns.lexdev.systempro_api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.ddns.lexdev.systempro_api.domain.Person;

@Service
public class SupportPersonService {

    private final PersonService personService;

    public SupportPersonService(PersonService personService) {
        this.personService = personService;
    }

    @Transactional
    public Person reactivatePerson(Long personId) {
        return personService.reactivate(personId);
    }
}
