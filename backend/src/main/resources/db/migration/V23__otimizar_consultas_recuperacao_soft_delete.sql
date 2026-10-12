-- ============================================================
-- V23 - Otimizar consultas do módulo de recuperação (soft delete)
--
-- Índices parciais: armazenam somente registros excluídos.
-- Atendem ao filtro active = false e à ordenação usada pela tela
-- de recuperação, sem aumentar desnecessariamente os índices dos
-- registros ativos do sistema.
-- ============================================================

CREATE INDEX idx_client_deleted_updated
    ON tb_client (updated_at DESC, id DESC)
    WHERE active = false;

CREATE INDEX idx_supplier_deleted_updated
    ON tb_supplier (updated_at DESC, id DESC)
    WHERE active = false;

CREATE INDEX idx_product_deleted_updated
    ON tb_product (updated_at DESC, id DESC)
    WHERE active = false;

CREATE INDEX idx_supplier_documents_deleted_updated
    ON tb_supplier_documents (updated_at DESC, id DESC)
    WHERE active = false;

CREATE INDEX idx_users_deleted_id
    ON tb_users (id DESC)
    WHERE active = false;