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

    @Query("SELECT DISTINCT s FROM Supplier s JOIN FETCH s.person")
    Page<Supplier> findAllWithPerson(Pageable pageable);
}
