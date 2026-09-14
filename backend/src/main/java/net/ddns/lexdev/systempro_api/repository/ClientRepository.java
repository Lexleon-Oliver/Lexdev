package net.ddns.lexdev.systempro_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import net.ddns.lexdev.systempro_api.domain.Client;

public interface ClientRepository extends JpaRepository<Client, Long> {
    boolean existsByCpfCnpj(String cpfCnpj);
    Optional<Client> findByCpfCnpj(String cpfCnpj);
}
