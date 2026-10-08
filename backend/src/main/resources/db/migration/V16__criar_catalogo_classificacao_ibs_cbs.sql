-- Snapshot local da tabela oficial CST/cClassTrib. Não há seed parcial: o catálogo
-- deve ser carregado integralmente a partir da publicação oficial vigente antes de
-- a emissão RTC ser habilitada.
CREATE TABLE tb_ibs_cbs_tax_classification (
    c_class_trib VARCHAR(6) PRIMARY KEY,
    cst VARCHAR(3) NOT NULL,
    description VARCHAR(500) NOT NULL,
    taxation_mode VARCHAR(40) NOT NULL,
    nfce_allowed BOOLEAN NOT NULL,
    ibs_reduction_percent NUMERIC(7,4),
    cbs_reduction_percent NUMERIC(7,4),
    valid_from DATE NOT NULL,
    valid_to DATE,
    source_version VARCHAR(40) NOT NULL,
    CONSTRAINT ck_ibs_cbs_class_code CHECK (c_class_trib ~ '^[0-9]{6}$'),
    CONSTRAINT ck_ibs_cbs_cst_code CHECK (cst ~ '^[0-9]{3}$'),
    CONSTRAINT ck_ibs_cbs_class_matches_cst CHECK (LEFT(c_class_trib, 3) = cst),
    CONSTRAINT ck_ibs_reduction_percent CHECK (ibs_reduction_percent IS NULL OR (ibs_reduction_percent >= 0 AND ibs_reduction_percent <= 100)),
    CONSTRAINT ck_cbs_reduction_percent CHECK (cbs_reduction_percent IS NULL OR (cbs_reduction_percent >= 0 AND cbs_reduction_percent <= 100)),
    CONSTRAINT ck_ibs_cbs_validity CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX idx_ibs_cbs_tax_classification_cst ON tb_ibs_cbs_tax_classification(cst);
CREATE INDEX idx_ibs_cbs_tax_classification_validity ON tb_ibs_cbs_tax_classification(valid_from, valid_to);
