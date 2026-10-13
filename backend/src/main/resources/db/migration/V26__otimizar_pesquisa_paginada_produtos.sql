-- Pesquisa textual paginada de produtos.
-- pg_trgm permite que buscas ILIKE/LIKE com %termo% usem índice.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_product_name_trgm_active
    ON tb_product USING gin (lower(name) gin_trgm_ops)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_product_code_trgm_active
    ON tb_product USING gin (lower(code) gin_trgm_ops)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_product_model_trgm_active
    ON tb_product USING gin (lower(model) gin_trgm_ops)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_product_manufacturer_code_trgm_active
    ON tb_product USING gin (lower(manufacturer_code) gin_trgm_ops)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_product_gtin_trgm_active
    ON tb_product USING gin (lower(gtin) gin_trgm_ops)
    WHERE active = true;