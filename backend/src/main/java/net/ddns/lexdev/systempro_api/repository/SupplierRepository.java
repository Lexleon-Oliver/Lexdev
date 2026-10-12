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
            SELECT s
            FROM Supplier s
            JOIN FETCH s.person p
            WHERE :term = ''
               OR LOWER(p.name) LIKE CONCAT('%', :term, '%')
               OR (:digits <> '' AND p.cpfCnpj LIKE CONCAT('%', :digits, '%'))
               OR LOWER(COALESCE(s.categoria, '')) LIKE CONCAT('%', :term, '%')
               OR EXISTS (
                   SELECT pc.id
                   FROM PersonContact pc
                   WHERE pc.person = p
                     AND (
                         LOWER(pc.value) LIKE CONCAT('%', :term, '%')
                         OR (:digits <> '' AND pc.value LIKE CONCAT('%', :digits, '%'))
                     )
               )
            """,
        countQuery = """
            SELECT COUNT(s)
            FROM Supplier s
            JOIN s.person p
            WHERE :term = ''
               OR LOWER(p.name) LIKE CONCAT('%', :term, '%')
               OR (:digits <> '' AND p.cpfCnpj LIKE CONCAT('%', :digits, '%'))
               OR LOWER(COALESCE(s.categoria, '')) LIKE CONCAT('%', :term, '%')
               OR EXISTS (
                   SELECT pc.id
                   FROM PersonContact pc
                   WHERE pc.person = p
                     AND (
                         LOWER(pc.value) LIKE CONCAT('%', :term, '%')
                         OR (:digits <> '' AND pc.value LIKE CONCAT('%', :digits, '%'))
                     )
               )
            """
    )
    Page<Supplier> search(
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