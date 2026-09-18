package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.ddns.lexdev.systempro_api.domain.Person;

public interface PersonRepository extends JpaRepository<Person, Long> {

    Optional<Person> findByCpfCnpj(String cpfCnpj);

    @Query(value = """
        SELECT *
        FROM tb_person
        WHERE cpf_cnpj = :cpfCnpj
        LIMIT 1
        """, nativeQuery = true)
    Optional<Person> findIncludingInactiveByCpfCnpj(
        @Param("cpfCnpj") String cpfCnpj
    );

    @Query(value = """
        SELECT *
        FROM tb_person
        WHERE id = :id
        LIMIT 1
        """, nativeQuery = true)
    Optional<Person> findIncludingInactiveById(
        @Param("id") Long id
    );
}
