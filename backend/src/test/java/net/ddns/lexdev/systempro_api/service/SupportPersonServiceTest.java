package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import net.ddns.lexdev.systempro_api.domain.Person;

@ExtendWith(MockitoExtension.class)
class SupportPersonServiceTest {

    @Mock
    private PersonService personService;

    @InjectMocks
    private SupportPersonService service;

    @Test
    @DisplayName("Deve reativar Person através do PersonService")
    void deveReativarPersonAtravésDoPersonService() {

        Person person = new Person();
        person.setActive(true);

        when(personService.reactivate(10L))
            .thenReturn(person);

        Person result =
            service.reactivatePerson(10L);

        assertThat(result)
            .isSameAs(person);

        assertThat(result.isActive())
            .isTrue();

        verify(personService)
            .reactivate(10L);
    }
}
