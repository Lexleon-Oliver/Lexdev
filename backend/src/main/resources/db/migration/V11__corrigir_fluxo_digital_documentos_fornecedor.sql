BEGIN;

ALTER TABLE tb_supplier_document_versions
    DROP CONSTRAINT IF EXISTS ck_supplier_document_versions_scan_status;

ALTER TABLE tb_supplier_document_versions
    ADD CONSTRAINT ck_supplier_document_versions_scan_status
    CHECK (scan_status IN ('PENDING_SCAN', 'CLEAN', 'QUARANTINED'));

ALTER TABLE tb_supplier_document_versions
    ALTER COLUMN created_by SET NOT NULL;

ALTER TABLE tb_supplier_document_versions
    ADD CONSTRAINT ck_supplier_document_versions_version_positive
    CHECK (version_number > 0);

ALTER TABLE tb_supplier_document_versions
    ADD CONSTRAINT ck_supplier_document_versions_file_size_positive
    CHECK (file_size > 0);

COMMIT;
