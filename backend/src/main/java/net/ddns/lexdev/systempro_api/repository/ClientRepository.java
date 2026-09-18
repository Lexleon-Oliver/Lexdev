package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.ddns.lexdev.systempro_api.domain.Client;

public interface ClientRepository extends JpaRepository<Client, Long> {
    boolean existsByPersonCpfCnpj(String cpfCnpj);
    
    @Query("SELECT c FROM Client c JOIN FETCH c.person WHERE c.id = :id")
    Optional<Client> findByIdWithPerson(@Param("id") Long id);

    @Query("SELECT c FROM Client c JOIN FETCH c.person")
    Page<Client> findAllWithPerson(Pageable pageable);

    @Query(value = """
        SELECT c.id
        FROM tb_client c
        JOIN tb_person p ON p.id = c.person_id
        WHERE p.cpf_cnpj = :cpfCnpj
        LIMIT 1
        """, nativeQuery = true)
    Optional<Long> findAnyByPersonCpfCnpj(
        @Param("cpfCnpj") String cpfCnpj
    );
}
