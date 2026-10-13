package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.ddns.lexdev.systempro_api.domain.Client;
import net.ddns.lexdev.systempro_api.repository.projection.ClientSummaryProjection;

public interface ClientRepository extends JpaRepository<Client, Long> {
    boolean existsByPersonCpfCnpj(String cpfCnpj);
    
    @Query("SELECT c FROM Client c JOIN FETCH c.person WHERE c.id = :id")
    Optional<Client> findByIdWithPerson(@Param("id") Long id);

    @Query(
        value = """
            SELECT
                c.id AS id,
                p.id AS "personId",
                p.tipo_pessoa AS "tipoPessoa",
                p.name AS name,
                p.cpf_cnpj AS "cpfCnpj",
                (
                    SELECT pc.value
                    FROM tb_person_contact pc
                    WHERE pc.person_id = p.id
                      AND pc.type = 'EMAIL'
                    ORDER BY pc.id
                    LIMIT 1
                ) AS email,
                (
                    SELECT pc.value
                    FROM tb_person_contact pc
                    WHERE pc.person_id = p.id
                      AND pc.type <> 'EMAIL'
                    ORDER BY
                        CASE WHEN pc.principal THEN 0 ELSE 1 END,
                        CASE pc.type
                            WHEN 'CELULAR' THEN 0
                            WHEN 'WHATSAPP' THEN 1
                            WHEN 'TELEFONE' THEN 2
                            ELSE 3
                        END,
                        pc.id
                    LIMIT 1
                ) AS phone,
                c.active AS active
            FROM tb_client c
            JOIN tb_person p ON p.id = c.person_id
            WHERE c.active = true
              AND p.active = true
              AND (
                  :term = ''
                  OR LOWER(p.name) LIKE CONCAT('%', :term, '%')
                  OR (:digits <> '' AND p.cpf_cnpj LIKE CONCAT('%', :digits, '%'))
                  OR EXISTS (
                      SELECT 1
                      FROM tb_person_contact pc
                      WHERE pc.person_id = p.id
                        AND (
                            LOWER(pc.value) LIKE CONCAT('%', :term, '%')
                            OR (:digits <> '' AND pc.value LIKE CONCAT('%', :digits, '%'))
                        )
                  )
              )
            ORDER BY LOWER(p.name), c.id
            """,
        countQuery = """
            SELECT COUNT(*)
            FROM tb_client c
            JOIN tb_person p ON p.id = c.person_id
            WHERE c.active = true
              AND p.active = true
              AND (
                  :term = ''
                  OR LOWER(p.name) LIKE CONCAT('%', :term, '%')
                  OR (:digits <> '' AND p.cpf_cnpj LIKE CONCAT('%', :digits, '%'))
                  OR EXISTS (
                      SELECT 1
                      FROM tb_person_contact pc
                      WHERE pc.person_id = p.id
                        AND (
                            LOWER(pc.value) LIKE CONCAT('%', :term, '%')
                            OR (:digits <> '' AND pc.value LIKE CONCAT('%', :digits, '%'))
                        )
                  )
              )
            """,
        nativeQuery = true
    )
    Page<ClientSummaryProjection> searchSummary(
        @Param("term") String term,
        @Param("digits") String digits,
        Pageable pageable
    );

    @Query("SELECT c FROM Client c JOIN FETCH c.person WHERE c.person.cpfCnpj = :cpfCnpj")
    Optional<Client> findByPersonCpfCnpj(@Param("cpfCnpj") String cpfCnpj);

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