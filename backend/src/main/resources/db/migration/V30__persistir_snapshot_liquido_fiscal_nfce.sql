-- NT 2026.008: os valores sem tributos não podem ser derivados do total do PDV.
-- Campos opcionais, preenchidos apenas por um cálculo fiscal confiável e versionado.
-- A ausência total preserva vendas históricas e o fluxo de emissão atual.
ALTER TABLE tb_sale_item
    ADD COLUMN nfce_liquid_unit_without_taxes NUMERIC(21,10),
    ADD COLUMN nfce_liquid_total_without_taxes NUMERIC(15,2),
    ADD COLUMN nfce_liquid_calculation_source VARCHAR(80);

ALTER TABLE tb_sale_item
    ADD CONSTRAINT ck_sale_item_nfce_liquid_snapshot_complete
    CHECK (
        (nfce_liquid_unit_without_taxes IS NULL
            AND nfce_liquid_total_without_taxes IS NULL
            AND nfce_liquid_calculation_source IS NULL)
        OR
        (nfce_liquid_unit_without_taxes IS NOT NULL
            AND nfce_liquid_total_without_taxes IS NOT NULL
            AND nfce_liquid_calculation_source IS NOT NULL
            AND LENGTH(nfce_liquid_calculation_source) >= 3
            AND nfce_liquid_unit_without_taxes >= 0
            AND nfce_liquid_total_without_taxes >= 0)
    );
