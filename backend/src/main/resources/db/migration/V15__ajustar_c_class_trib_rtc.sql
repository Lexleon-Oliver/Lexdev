-- NT/IT RTC: cClassTrib possui 6 posições. A redução é intencionalmente
-- não destrutiva: PostgreSQL interromperá a migração se houver valor legado
-- maior que 6 caracteres, exigindo saneamento explícito em vez de truncamento.
ALTER TABLE tb_product_fiscal_profile
    ALTER COLUMN c_class_trib TYPE VARCHAR(6);
