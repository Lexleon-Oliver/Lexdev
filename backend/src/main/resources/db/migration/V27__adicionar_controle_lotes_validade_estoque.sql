ALTER TABLE tb_product
    ADD COLUMN controls_expiration BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE tb_stock_lot (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    lot_code VARCHAR(100) NOT NULL,
    manufacture_date DATE,
    expiration_date DATE NOT NULL,
    quantity NUMERIC(19,6) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_stock_lot_product FOREIGN KEY (product_id) REFERENCES tb_product(id) ON DELETE RESTRICT,
    CONSTRAINT uk_stock_lot_product_code_expiration UNIQUE (product_id, lot_code, expiration_date),
    CONSTRAINT ck_stock_lot_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT ck_stock_lot_dates CHECK (manufacture_date IS NULL OR manufacture_date <= expiration_date)
);

CREATE INDEX idx_stock_lot_product_fefo
    ON tb_stock_lot (product_id, expiration_date ASC, id ASC)
    WHERE quantity > 0;

CREATE TABLE tb_stock_movement_lot (
    id BIGSERIAL PRIMARY KEY,
    stock_movement_id BIGINT NOT NULL,
    stock_lot_id BIGINT NOT NULL,
    quantity NUMERIC(19,6) NOT NULL,
    CONSTRAINT fk_stock_movement_lot_movement FOREIGN KEY (stock_movement_id) REFERENCES tb_stock_movement(id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movement_lot_lot FOREIGN KEY (stock_lot_id) REFERENCES tb_stock_lot(id) ON DELETE RESTRICT,
    CONSTRAINT ck_stock_movement_lot_quantity_positive CHECK (quantity > 0),
    CONSTRAINT uk_stock_movement_lot UNIQUE (stock_movement_id, stock_lot_id)
);

CREATE INDEX idx_stock_movement_lot_lot ON tb_stock_movement_lot (stock_lot_id);