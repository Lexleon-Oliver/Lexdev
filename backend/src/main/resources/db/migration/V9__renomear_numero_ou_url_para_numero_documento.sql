BEGIN;

ALTER TABLE tb_supplier_documents
    RENAME COLUMN numero_ou_url TO numero_documento;

COMMIT;
