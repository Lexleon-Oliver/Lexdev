package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;

public interface PersonRepository extends JpaRepository<Person, Long> {

    Optional<Person> findByCpfCnpj(String cpfCnpj);

    @Query("""
        SELECT p FROM Person p
        LEFT JOIN FETCH p.legalEntity
        WHERE p.tipoPessoa = :type
          AND (
              :search = ''
              OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
              OR p.cpfCnpj LIKE CONCAT('%', :search, '%')
          )
        """)
    Page<Person> searchByType(
        @Param("type") TipoPessoa type,
        @Param("search") String search,
        Pageable pageable
    );

    @Query(value = """
        SELECT * FROM tb_person WHERE cpf_cnpj = :cpfCnpj LIMIT 1
        """, nativeQuery = true)
    Optional<Person> findIncludingInactiveByCpfCnpj(@Param("cpfCnpj") String cpfCnpj);

    @Query(value = """
        SELECT * FROM tb_person WHERE id = :id LIMIT 1
        """, nativeQuery = true)
    Optional<Person> findIncludingInactiveById(@Param("id") Long id);
}