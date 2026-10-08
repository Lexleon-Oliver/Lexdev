ALTER TABLE tb_fiscal_document
    ADD COLUMN contingency_at TIMESTAMPTZ,
    ADD COLUMN contingency_justification VARCHAR(256);

ALTER TABLE tb_fiscal_document
    ADD CONSTRAINT ck_fiscal_document_contingency_data
    CHECK (
        (emission_type <> 'CONTINGENCIA_OFFLINE' AND contingency_at IS NULL AND contingency_justification IS NULL)
        OR
        (emission_type = 'CONTINGENCIA_OFFLINE' AND contingency_at IS NOT NULL AND contingency_justification IS NOT NULL
            AND char_length(trim(contingency_justification)) BETWEEN 15 AND 256)
    );
