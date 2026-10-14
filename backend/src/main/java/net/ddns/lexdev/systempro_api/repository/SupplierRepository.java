package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.ddns.lexdev.systempro_api.domain.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    
    boolean existsByPersonCpfCnpj(String cpfCnpj);

    @Query("SELECT DISTINCT s FROM Supplier s JOIN FETCH s.person WHERE s.id = :id")
    Optional<Supplier> findByIdWithPerson(@Param("id") Long id);

    @Query(
        value = """
            SELECT
                s.id AS id,
                p.id AS personId,
                p.tipo_pessoa AS tipoPessoa,
                p.name AS name,
                p.cpf_cnpj AS cpfCnpj,
                email_contact.value AS email,
                phone_contact.value AS phone,
                s.categoria AS categoria
            FROM tb_supplier s
            JOIN tb_person p ON p.id = s.person_id
            LEFT JOIN LATERAL (
                SELECT pc.value
                FROM tb_person_contact pc
                WHERE pc.person_id = p.id
                  AND pc.type = 'EMAIL'
                ORDER BY pc.principal DESC, pc.id ASC
                LIMIT 1
            ) email_contact ON TRUE
            LEFT JOIN LATERAL (
                SELECT pc.value
                FROM tb_person_contact pc
                WHERE pc.person_id = p.id
                  AND pc.type = 'TELEFONE'
                ORDER BY pc.principal DESC, pc.id ASC
                LIMIT 1
            ) phone_contact ON TRUE
            WHERE s.active = TRUE
              AND p.active = TRUE
              AND (
                  :term = ''
                  OR LOWER(p.name) LIKE CONCAT('%', :term, '%')
                  OR (:digits <> '' AND p.cpf_cnpj LIKE CONCAT('%', :digits, '%'))
                  OR LOWER(COALESCE(s.categoria, '')) LIKE CONCAT('%', :term, '%')
                  OR EXISTS (
                      SELECT 1
                      FROM tb_person_contact pc_search
                      WHERE pc_search.person_id = p.id
                        AND (
                            LOWER(pc_search.value) LIKE CONCAT('%', :term, '%')
                            OR (:digits <> '' AND pc_search.value LIKE CONCAT('%', :digits, '%'))
                        )
                  )
              )
            ORDER BY LOWER(p.name), s.id
            """,
        countQuery = """
            SELECT COUNT(*)
            FROM tb_supplier s
            JOIN tb_person p ON p.id = s.person_id
            WHERE s.active = TRUE
              AND p.active = TRUE
              AND (
                  :term = ''
                  OR LOWER(p.name) LIKE CONCAT('%', :term, '%')
                  OR (:digits <> '' AND p.cpf_cnpj LIKE CONCAT('%', :digits, '%'))
                  OR LOWER(COALESCE(s.categoria, '')) LIKE CONCAT('%', :term, '%')
                  OR EXISTS (
                      SELECT 1
                      FROM tb_person_contact pc_search
                      WHERE pc_search.person_id = p.id
                        AND (
                            LOWER(pc_search.value) LIKE CONCAT('%', :term, '%')
                            OR (:digits <> '' AND pc_search.value LIKE CONCAT('%', :digits, '%'))
                        )
                  )
              )
            """,
        nativeQuery = true
    )
    Page<net.ddns.lexdev.systempro_api.repository.projection.SupplierSummaryProjection> search(
        @Param("term") String term,
        @Param("digits") String digits,
        Pageable pageable
    );

    @Query(value = """
        SELECT s.id
        FROM tb_supplier s
        JOIN tb_person p ON p.id = s.person_id
        WHERE p.cpf_cnpj = :cpfCnpj
        LIMIT 1
        """, nativeQuery = true)
    Optional<Long> findAnyByPersonCpfCnpj(
        @Param("cpfCnpj") String cpfCnpj
    );
}