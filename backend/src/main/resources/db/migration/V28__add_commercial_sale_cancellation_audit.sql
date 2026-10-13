ALTER TABLE tb_sale
    ADD COLUMN commercial_canceled_at TIMESTAMPTZ,
    ADD COLUMN commercial_cancellation_reason VARCHAR(500);