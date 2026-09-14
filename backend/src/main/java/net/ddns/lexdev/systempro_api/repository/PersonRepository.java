package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import net.ddns.lexdev.systempro_api.domain.Person;

public interface PersonRepository extends JpaRepository<Person, Long> {
    Optional<Person> findByCpfCnpj(String cpfCnpj);
}
