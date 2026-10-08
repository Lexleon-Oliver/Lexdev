-- Snapshot imutável do resultado RTC por item da venda.
-- Campos opcionais enquanto a emissão IBS/CBS permanece bloqueada.
ALTER TABLE tb_sale_item
    ADD COLUMN ibs_cbs_cst_snapshot VARCHAR(3),
    ADD COLUMN c_class_trib_snapshot VARCHAR(6),
    ADD COLUMN ibs_cbs_tax_base NUMERIC(19,2),
    ADD COLUMN ibs_uf_rate NUMERIC(9,6),
    ADD COLUMN ibs_uf_amount NUMERIC(19,2),
    ADD COLUMN ibs_municipal_rate NUMERIC(9,6),
    ADD COLUMN ibs_municipal_amount NUMERIC(19,2),
    ADD COLUMN ibs_total_amount NUMERIC(19,2),
    ADD COLUMN cbs_rate_rtc NUMERIC(9,6),
    ADD COLUMN cbs_amount NUMERIC(19,2);

ALTER TABLE tb_sale_item
    ADD CONSTRAINT ck_sale_item_ibs_cbs_snapshot_complete CHECK (
        (ibs_cbs_cst_snapshot IS NULL
         AND c_class_trib_snapshot IS NULL
         AND ibs_cbs_tax_base IS NULL
         AND ibs_uf_rate IS NULL
         AND ibs_uf_amount IS NULL
         AND ibs_municipal_rate IS NULL
         AND ibs_municipal_amount IS NULL
         AND ibs_total_amount IS NULL
         AND cbs_rate_rtc IS NULL
         AND cbs_amount IS NULL)
        OR
        (ibs_cbs_cst_snapshot IS NOT NULL
         AND c_class_trib_snapshot IS NOT NULL
         AND ibs_cbs_tax_base IS NOT NULL
         AND ibs_uf_rate IS NOT NULL
         AND ibs_uf_amount IS NOT NULL
         AND ibs_municipal_rate IS NOT NULL
         AND ibs_municipal_amount IS NOT NULL
         AND ibs_total_amount IS NOT NULL
         AND cbs_rate_rtc IS NOT NULL
         AND cbs_amount IS NOT NULL)
    );

ALTER TABLE tb_sale_item
    ADD CONSTRAINT ck_sale_item_ibs_cbs_snapshot_codes CHECK (
        ibs_cbs_cst_snapshot IS NULL
        OR (
            ibs_cbs_cst_snapshot ~ '^[0-9]{3}$'
            AND c_class_trib_snapshot ~ '^[0-9]{6}$'
            AND LEFT(c_class_trib_snapshot, 3) = ibs_cbs_cst_snapshot
        )
    );

ALTER TABLE tb_sale_item
    ADD CONSTRAINT ck_sale_item_ibs_cbs_snapshot_non_negative CHECK (
        ibs_cbs_tax_base IS NULL
        OR (
            ibs_cbs_tax_base >= 0
            AND ibs_uf_rate >= 0
            AND ibs_uf_amount >= 0
            AND ibs_municipal_rate >= 0
            AND ibs_municipal_amount >= 0
            AND ibs_total_amount >= 0
            AND cbs_rate_rtc >= 0
            AND cbs_amount >= 0
        )
    );
