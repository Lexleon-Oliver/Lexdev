CREATE TABLE tb_soft_delete_recovery_audit (
    id BIGSERIAL PRIMARY KEY,
    record_type VARCHAR(40) NOT NULL,
    record_id BIGINT NOT NULL,
    record_title VARCHAR(255) NOT NULL,
    record_subtitle VARCHAR(255),
    restored_by BIGINT NOT NULL,
    restored_by_username VARCHAR(50) NOT NULL,
    restored_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_soft_delete_recovery_audit_user
        FOREIGN KEY (restored_by) REFERENCES tb_users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_soft_delete_recovery_audit_record
    ON tb_soft_delete_recovery_audit (record_type, record_id);

CREATE INDEX idx_soft_delete_recovery_audit_restored_at
    ON tb_soft_delete_recovery_audit (restored_at DESC);

CREATE INDEX idx_soft_delete_recovery_audit_restored_by
    ON tb_soft_delete_recovery_audit (restored_by);