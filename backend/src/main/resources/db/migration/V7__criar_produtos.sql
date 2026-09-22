CREATE TABLE tb_product (
    id BIGSERIAL PRIMARY KEY,

    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,

    model VARCHAR(100),
    manufacturer_code VARCHAR(100),
    gtin VARCHAR(14),

    status VARCHAR(20) NOT NULL DEFAULT 'ATIVO',

    unit_of_measure VARCHAR(10) NOT NULL DEFAULT 'UN',
    controls_stock BOOLEAN NOT NULL DEFAULT TRUE,

    sale_price NUMERIC(19,4),
    minimum_sale_price NUMERIC(19,4),

    minimum_stock NUMERIC(19,4),
    maximum_stock NUMERIC(19,4),
    reorder_point NUMERIC(19,4),

    ncm VARCHAR(8),
    cest VARCHAR(7),
    origin VARCHAR(1),

    gross_weight NUMERIC(15,4),
    net_weight NUMERIC(15,4),

    height NUMERIC(15,4),
    width NUMERIC(15,4),
    length NUMERIC(15,4),

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_product_code UNIQUE (code)
);

CREATE INDEX idx_product_name
    ON tb_product (name);

CREATE INDEX idx_product_gtin
    ON tb_product (gtin);

CREATE INDEX idx_product_status
    ON tb_product (status);


CREATE TABLE tb_product_supplier (
    id BIGSERIAL PRIMARY KEY,

    product_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,

    supplier_code VARCHAR(100),

    purchase_price NUMERIC(19,4),
    lead_time_days INTEGER,
    minimum_order_quantity NUMERIC(19,4),

    preferred BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_product_supplier_product
        FOREIGN KEY (product_id)
        REFERENCES tb_product (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_product_supplier_supplier
        FOREIGN KEY (supplier_id)
        REFERENCES tb_supplier (id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_product_supplier
        UNIQUE (product_id, supplier_id)
);

CREATE INDEX idx_product_supplier_product
    ON tb_product_supplier (product_id);

CREATE INDEX idx_product_supplier_supplier
    ON tb_product_supplier (supplier_id);


CREATE TABLE tb_product_image (
    id BIGSERIAL PRIMARY KEY,

    product_id BIGINT NOT NULL,

    file_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(100),

    main_image BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT fk_product_image_product
        FOREIGN KEY (product_id)
        REFERENCES tb_product (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_product_image_product
    ON tb_product_image (product_id);

CREATE INDEX idx_product_image_order
    ON tb_product_image (product_id, sort_order);