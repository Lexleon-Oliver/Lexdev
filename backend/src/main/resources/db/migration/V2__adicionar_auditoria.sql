-- ============================================================
-- V2 - Adiciona auditoria e controle de concorrência
-- ============================================================

-- ------------------------------------------------------------
-- TB_PERSON
-- ------------------------------------------------------------

ALTER TABLE tb_person
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN version BIGINT;

UPDATE tb_person
SET
    created_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP,
    version = 0;

ALTER TABLE tb_person
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN version SET NOT NULL;


-- ------------------------------------------------------------
-- TB_CLIENT
-- ------------------------------------------------------------

ALTER TABLE tb_client
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN version BIGINT;

UPDATE tb_client
SET
    created_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP,
    version = 0;

ALTER TABLE tb_client
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN version SET NOT NULL;


-- ------------------------------------------------------------
-- TB_SUPPLIER
-- ------------------------------------------------------------

ALTER TABLE tb_supplier
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN version BIGINT;

UPDATE tb_supplier
SET
    created_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP,
    version = 0;

ALTER TABLE tb_supplier
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN version SET NOT NULL;