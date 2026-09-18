-- ============================================================
-- V6 - Remover campos antigos de tb_person
-- ============================================================

ALTER TABLE public.tb_person
    DROP COLUMN rg_ie,
    DROP COLUMN nome_fantasia,
    DROP COLUMN email,
    DROP COLUMN phone,
    DROP COLUMN cep,
    DROP COLUMN logradouro,
    DROP COLUMN numero,
    DROP COLUMN complemento,
    DROP COLUMN bairro,
    DROP COLUMN cidade,
    DROP COLUMN uf;