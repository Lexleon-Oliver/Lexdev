package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import net.ddns.lexdev.systempro_api.domain.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findFirstByOrderByIdAsc();

}
