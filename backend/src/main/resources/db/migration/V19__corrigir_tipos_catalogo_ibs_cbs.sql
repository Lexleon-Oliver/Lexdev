ALTER TABLE tb_ibs_cbs_tax_classification
    ALTER COLUMN gross_revenue_type TYPE INTEGER
        USING gross_revenue_type::INTEGER,
    ALTER COLUMN donation_type TYPE INTEGER
        USING donation_type::INTEGER;