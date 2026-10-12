-- Pesquisa server-side de fornecedores em bases grandes.
-- A extensão e os índices compartilhados com clientes são idempotentes.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_person_name_trgm_active
    ON public.tb_person USING gin (lower(name) gin_trgm_ops)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_person_cpf_cnpj_trgm_active
    ON public.tb_person USING gin (cpf_cnpj gin_trgm_ops)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_person_contact_value_trgm
    ON public.tb_person_contact USING gin (lower(value) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_supplier_categoria_trgm_active
    ON public.tb_supplier USING gin (lower(categoria) gin_trgm_ops)
    WHERE active = true;