package net.ddns.lexdev.systempro_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import net.ddns.lexdev.systempro_api.domain.OperationalSettingAudit;

public interface OperationalSettingAuditRepository extends JpaRepository<OperationalSettingAudit, Long> {}
