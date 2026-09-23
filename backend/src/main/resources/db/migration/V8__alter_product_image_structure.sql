-- ============================================================
-- 1. Tamanho do arquivo
-- ============================================================

ALTER TABLE tb_product_image
    ADD COLUMN file_size BIGINT NOT NULL DEFAULT 0;


-- ============================================================
-- 2. Corrige possíveis múltiplas imagens principais
--
-- Mantém como principal somente a imagem de menor ID
-- para cada produto.
-- ============================================================

WITH ranked_images AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY product_id
            ORDER BY id
        ) AS position
    FROM tb_product_image
    WHERE main_image = true
)
UPDATE tb_product_image pi
SET main_image = false
FROM ranked_images ri
WHERE pi.id = ri.id
  AND ri.position > 1;


-- ============================================================
-- 3. Garante no banco que cada produto tenha
-- no máximo uma imagem principal
-- ============================================================

CREATE UNIQUE INDEX ux_product_image_main
    ON tb_product_image (product_id)
    WHERE main_image = true;


-- ============================================================
-- 4. Índice para ordenação das imagens por produto
-- ============================================================

CREATE INDEX ix_product_image_product_sort
    ON tb_product_image (product_id, sort_order, id);