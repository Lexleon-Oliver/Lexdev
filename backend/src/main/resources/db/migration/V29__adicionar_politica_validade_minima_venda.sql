CREATE TABLE tb_stock_settings (
    id SMALLINT PRIMARY KEY,
    min_sellable_shelf_life_days INTEGER NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_stock_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_stock_settings_min_shelf_life CHECK (min_sellable_shelf_life_days BETWEEN 0 AND 3650)
);

INSERT INTO tb_stock_settings (id, min_sellable_shelf_life_days, version)
VALUES (1, 1, 0);