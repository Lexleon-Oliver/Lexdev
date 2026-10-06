CREATE TABLE tb_fiscal_establishment (
    id BIGSERIAL PRIMARY KEY,
    person_id BIGINT NOT NULL UNIQUE,
    municipality_ibge_code VARCHAR(7) NOT NULL,
    tax_regime VARCHAR(30) NOT NULL,
    environment VARCHAR(20) NOT NULL DEFAULT 'HOMOLOGACAO',
    series INTEGER NOT NULL DEFAULT 1,
    next_number BIGINT NOT NULL DEFAULT 1,
    certificate_storage_key VARCHAR(500),
    encrypted_certificate_password TEXT,
    csc_id INTEGER,
    encrypted_csc TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_fiscal_establishment_person FOREIGN KEY (person_id) REFERENCES tb_person(id) ON DELETE RESTRICT,
    CONSTRAINT ck_fiscal_establishment_series CHECK (series BETWEEN 1 AND 999),
    CONSTRAINT ck_fiscal_establishment_next_number CHECK (next_number >= 1)
);

CREATE TABLE tb_product_fiscal_profile (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    cfop VARCHAR(4) NOT NULL,
    icms_cst_csosn VARCHAR(3) NOT NULL,
    pis_cst VARCHAR(2) NOT NULL,
    cofins_cst VARCHAR(2) NOT NULL,
    icms_rate NUMERIC(7,4),
    pis_rate NUMERIC(7,4),
    cofins_rate NUMERIC(7,4),
    ibs_cbs_cst VARCHAR(3),
    c_class_trib VARCHAR(10),
    ibs_rate NUMERIC(9,4),
    cbs_rate NUMERIC(9,4),
    additional_information TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_fiscal_profile_product FOREIGN KEY (product_id) REFERENCES tb_product(id) ON DELETE CASCADE
);

CREATE TABLE tb_sale (
    id BIGSERIAL PRIMARY KEY,
    fiscal_establishment_id BIGINT NOT NULL,
    client_id BIGINT,
    user_id BIGINT NOT NULL,
    consumer_cpf_cnpj VARCHAR(14),
    status VARCHAR(30) NOT NULL,
    sale_at TIMESTAMP WITH TIME ZONE NOT NULL,
    subtotal NUMERIC(19,4) NOT NULL,
    discount NUMERIC(19,4) NOT NULL,
    total NUMERIC(19,4) NOT NULL,
    note VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_sale_fiscal_establishment FOREIGN KEY (fiscal_establishment_id) REFERENCES tb_fiscal_establishment(id),
    CONSTRAINT fk_sale_client FOREIGN KEY (client_id) REFERENCES tb_client(id),
    CONSTRAINT fk_sale_user FOREIGN KEY (user_id) REFERENCES tb_users(id),
    CONSTRAINT ck_sale_consumer_document CHECK (consumer_cpf_cnpj IS NULL OR consumer_cpf_cnpj ~ '^[0-9]{11}$' OR consumer_cpf_cnpj ~ '^[0-9]{14}$')
);

CREATE INDEX idx_sale_sale_at ON tb_sale (sale_at DESC);
CREATE INDEX idx_sale_establishment ON tb_sale (fiscal_establishment_id, sale_at DESC);

CREATE TABLE tb_sale_item (
    id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    item_number INTEGER NOT NULL,
    code_snapshot VARCHAR(50) NOT NULL,
    name_snapshot VARCHAR(200) NOT NULL,
    unit_snapshot VARCHAR(10) NOT NULL,
    gtin_snapshot VARCHAR(14),
    ncm_snapshot VARCHAR(8),
    cest_snapshot VARCHAR(7),
    origin_snapshot VARCHAR(1),
    quantity NUMERIC(19,6) NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL,
    discount NUMERIC(19,4) NOT NULL,
    total NUMERIC(19,4) NOT NULL,
    cfop_snapshot VARCHAR(4) NOT NULL,
    icms_cst_csosn_snapshot VARCHAR(3) NOT NULL,
    pis_cst_snapshot VARCHAR(2) NOT NULL,
    cofins_cst_snapshot VARCHAR(2) NOT NULL,
    icms_rate NUMERIC(7,4),
    pis_rate NUMERIC(7,4),
    cofins_rate NUMERIC(7,4),
    CONSTRAINT fk_sale_item_sale FOREIGN KEY (sale_id) REFERENCES tb_sale(id) ON DELETE CASCADE,
    CONSTRAINT fk_sale_item_product FOREIGN KEY (product_id) REFERENCES tb_product(id) ON DELETE RESTRICT,
    CONSTRAINT uk_sale_item_number UNIQUE (sale_id, item_number)
);

CREATE TABLE tb_sale_payment (
    id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    card_brand VARCHAR(50),
    authorization_code VARCHAR(100),
    CONSTRAINT fk_sale_payment_sale FOREIGN KEY (sale_id) REFERENCES tb_sale(id) ON DELETE CASCADE
);

CREATE TABLE tb_fiscal_document (
    id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL UNIQUE,
    establishment_id BIGINT NOT NULL,
    model VARCHAR(2) NOT NULL DEFAULT '65',
    series INTEGER NOT NULL,
    number BIGINT NOT NULL,
    access_key VARCHAR(44) UNIQUE,
    emission_type VARCHAR(30) NOT NULL,
    status VARCHAR(40) NOT NULL,
    xml TEXT,
    response_xml TEXT,
    receipt_number VARCHAR(30),
    protocol VARCHAR(30),
    reason VARCHAR(1000),
    issued_at TIMESTAMP WITH TIME ZONE,
    canceled_at TIMESTAMP WITH TIME ZONE,
    cancellation_protocol VARCHAR(30),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_fiscal_document_sale FOREIGN KEY (sale_id) REFERENCES tb_sale(id) ON DELETE RESTRICT,
    CONSTRAINT fk_fiscal_document_establishment FOREIGN KEY (establishment_id) REFERENCES tb_fiscal_establishment(id) ON DELETE RESTRICT,
    CONSTRAINT uk_fiscal_document_number UNIQUE (establishment_id, series, number)
);

CREATE TABLE tb_fiscal_event (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    sequence_number INTEGER NOT NULL,
    status VARCHAR(40) NOT NULL,
    xml TEXT,
    response_xml TEXT,
    protocol VARCHAR(30),
    reason VARCHAR(1000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_fiscal_event_document FOREIGN KEY (document_id) REFERENCES tb_fiscal_document(id) ON DELETE RESTRICT,
    CONSTRAINT uk_fiscal_event_sequence UNIQUE (document_id, event_type, sequence_number)
);