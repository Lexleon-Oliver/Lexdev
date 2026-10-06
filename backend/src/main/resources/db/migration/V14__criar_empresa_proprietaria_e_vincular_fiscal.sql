BEGIN;

-- A empresa proprietária é uma entidade administrativa do ambiente do SystemPro.
-- O cadastro reaproveita Person/LegalEntity, mas deixa de depender dos papéis
-- comerciais de Cliente/Fornecedor.
CREATE TABLE tb_company (
    id BIGSERIAL PRIMARY KEY,
    person_id BIGINT NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_company_person
        FOREIGN KEY (person_id) REFERENCES tb_person(id) ON DELETE RESTRICT
);

-- Migração conservadora: se já houver uma configuração fiscal, ela define
-- a empresa proprietária inicial. Mais de uma PJ diferente é situação ambígua
-- e deve ser resolvida antes da migração, evitando associação incorreta.
DO $$
DECLARE
    distinct_person_count INTEGER;
BEGIN
    SELECT COUNT(DISTINCT person_id)
      INTO distinct_person_count
      FROM tb_fiscal_establishment;

    IF distinct_person_count > 1 THEN
        RAISE EXCEPTION
            'Não foi possível migrar a configuração fiscal: existem % pessoas jurídicas diferentes associadas a estabelecimentos fiscais. Resolva os dados antes da V14.',
            distinct_person_count;
    END IF;
END $$;

INSERT INTO tb_company (person_id)
SELECT MIN(person_id)
FROM tb_fiscal_establishment
HAVING COUNT(DISTINCT person_id) = 1;

ALTER TABLE tb_fiscal_establishment
    ADD COLUMN company_id BIGINT;

UPDATE tb_fiscal_establishment fe
SET company_id = c.id
FROM tb_company c
WHERE c.person_id = fe.person_id;

ALTER TABLE tb_fiscal_establishment
    ALTER COLUMN company_id SET NOT NULL;

ALTER TABLE tb_fiscal_establishment
    ADD CONSTRAINT fk_fiscal_establishment_company
        FOREIGN KEY (company_id) REFERENCES tb_company(id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uk_fiscal_establishment_company
    ON tb_fiscal_establishment (company_id);

ALTER TABLE tb_fiscal_establishment
    DROP CONSTRAINT IF EXISTS fk_fiscal_establishment_person;

ALTER TABLE tb_fiscal_establishment
    DROP CONSTRAINT IF EXISTS tb_fiscal_establishment_person_id_key;

ALTER TABLE tb_fiscal_establishment
    DROP COLUMN person_id;


COMMIT;
