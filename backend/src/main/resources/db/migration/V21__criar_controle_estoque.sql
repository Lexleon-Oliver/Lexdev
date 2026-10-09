CREATE TABLE tb_stock_balance (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    quantity NUMERIC(19,6) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_stock_balance_product
        FOREIGN KEY (product_id) REFERENCES tb_product(id) ON DELETE RESTRICT,
    CONSTRAINT uk_stock_balance_product UNIQUE (product_id),
    CONSTRAINT ck_stock_balance_quantity_non_negative CHECK (quantity >= 0)
);

CREATE TABLE tb_stock_movement (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    movement_type VARCHAR(40) NOT NULL,
    origin VARCHAR(40) NOT NULL,
    source_reference VARCHAR(120) NOT NULL,
    quantity NUMERIC(19,6) NOT NULL,
    previous_balance NUMERIC(19,6) NOT NULL,
    resulting_balance NUMERIC(19,6) NOT NULL,
    sale_id BIGINT,
    sale_item_id BIGINT,
    reason VARCHAR(500),
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_stock_movement_product
        FOREIGN KEY (product_id) REFERENCES tb_product(id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movement_sale
        FOREIGN KEY (sale_id) REFERENCES tb_sale(id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movement_sale_item
        FOREIGN KEY (sale_item_id) REFERENCES tb_sale_item(id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movement_created_by
        FOREIGN KEY (created_by) REFERENCES tb_users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_stock_movement_origin_reference UNIQUE (origin, source_reference),
    CONSTRAINT ck_stock_movement_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_stock_movement_previous_non_negative CHECK (previous_balance >= 0),
    CONSTRAINT ck_stock_movement_resulting_non_negative CHECK (resulting_balance >= 0),
    CONSTRAINT ck_stock_movement_balance_math CHECK (
        (movement_type IN ('INITIAL_BALANCE', 'PURCHASE_ENTRY', 'SALE_CANCELLATION_RETURN', 'POSITIVE_ADJUSTMENT')
            AND resulting_balance = previous_balance + quantity)
        OR
        (movement_type IN ('SALE_OUT', 'NEGATIVE_ADJUSTMENT')
            AND resulting_balance = previous_balance - quantity)
    ),
    CONSTRAINT ck_stock_movement_sale_reference CHECK (
        (origin IN ('SALE', 'SALE_CANCELLATION') AND sale_id IS NOT NULL AND sale_item_id IS NOT NULL)
        OR
        (origin NOT IN ('SALE', 'SALE_CANCELLATION') AND sale_id IS NULL AND sale_item_id IS NULL)
    )
);

CREATE INDEX idx_stock_movement_product_created_at
    ON tb_stock_movement (product_id, created_at DESC, id DESC);

CREATE INDEX idx_stock_movement_sale
    ON tb_stock_movement (sale_id)
    WHERE sale_id IS NOT NULL;

CREATE INDEX idx_stock_movement_sale_item
    ON tb_stock_movement (sale_item_id)
    WHERE sale_item_id IS NOT NULL;