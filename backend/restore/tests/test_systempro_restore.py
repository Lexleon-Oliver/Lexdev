import hashlib
import importlib.util
import json
import os
import tempfile
import unittest
import sys
import zipfile
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "systempro_restore.py"
spec = importlib.util.spec_from_file_location("systempro_restore", MODULE_PATH)
restore = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = restore
assert spec.loader is not None
spec.loader.exec_module(restore)


class PackageInspectionTest(unittest.TestCase):
    def make_package(self, root: Path, *, manifest_extra=None, tamper=False, extra_payload=None):
        payloads = {
            "database/systempro.dump": b"fake-custom-dump",
            "storage/product-images/a.jpg": b"image-content",
            "storage/supplier-documents/x.pdf": b"document-content",
        }
        if extra_payload:
            payloads[extra_payload] = b"unexpected"
        manifest = {
            "formatVersion": 1,
            "startedAt": "2026-10-14T11:49:45Z",
            "databaseDumpCompletedAt": "2026-10-14T11:49:46Z",
            "databaseEngine": "PostgreSQL",
            "databaseName": "lexdevdb",
            "storageProvider": "local",
            "includedComponents": ["database", "product-images", "supplier-documents"],
            "trigger": "MANUAL",
        }
        if manifest_extra:
            manifest.update(manifest_extra)
        checksums = "".join(
            hashlib.sha256(content).hexdigest() + "  " + name + "\n"
            for name, content in payloads.items()
        )
        package = root / "backup.spbackup"
        with zipfile.ZipFile(package, "w", zipfile.ZIP_DEFLATED) as zf:
            zf.writestr("manifest.json", json.dumps(manifest))
            zf.writestr("checksums.sha256", checksums)
            for name, content in payloads.items():
                zf.writestr(name, b"tampered" if tamper and name.endswith("a.jpg") else content)
        return package

    def test_accepts_valid_v1_and_legacy_completed_at(self):
        with tempfile.TemporaryDirectory() as directory:
            package = self.make_package(
                Path(directory),
                manifest_extra={"completedAt": "2026-10-14T11:49:47Z"},
            )
            info = restore.inspect_package(package, 10_000_000, 1000)
            self.assertEqual(1, info.manifest["formatVersion"])
            self.assertEqual("2026-10-14T11:49:47Z", info.manifest["completedAt"])
            self.assertEqual("lexdevdb", info.manifest["databaseName"])
            self.assertEqual(3, len(info.checksums))

    def test_rejects_checksum_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            package = self.make_package(Path(directory), tamper=True)
            with self.assertRaisesRegex(restore.RestoreError, "Checksum inválido"):
                restore.inspect_package(package, 10_000_000, 1000)

    def test_rejects_unexpected_payload(self):
        with tempfile.TemporaryDirectory() as directory:
            package = self.make_package(Path(directory), extra_payload="evil/file.txt")
            with self.assertRaisesRegex(restore.RestoreError, "Payload não permitido"):
                restore.inspect_package(package, 10_000_000, 1000)

    def test_rejects_path_traversal(self):
        with tempfile.TemporaryDirectory() as directory:
            package = Path(directory) / "backup.spbackup"
            manifest = {
                "formatVersion": 1,
                "databaseEngine": "PostgreSQL",
                "databaseName": "lexdevdb",
                "storageProvider": "local",
                "includedComponents": ["database", "product-images", "supplier-documents"],
            }
            dump = b"dump"
            traversal = b"x"
            checksums = (
                hashlib.sha256(dump).hexdigest() + "  database/systempro.dump\n"
                + hashlib.sha256(traversal).hexdigest() + "  storage/product-images/../x\n"
            )
            with zipfile.ZipFile(package, "w") as zf:
                zf.writestr("manifest.json", json.dumps(manifest))
                zf.writestr("checksums.sha256", checksums)
                zf.writestr("database/systempro.dump", dump)
                zf.writestr("storage/product-images/../x", traversal)
            with self.assertRaises(restore.RestoreError):
                restore.inspect_package(package, 10_000_000, 1000)

    def test_safe_extraction_produces_only_expected_payload(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            package = self.make_package(root)
            info = restore.inspect_package(package, 10_000_000, 1000)
            staging = root / "staging"
            restore.extract_payload(info, staging)
            self.assertEqual(b"fake-custom-dump", (staging / "database/systempro.dump").read_bytes())
            self.assertEqual(b"image-content", (staging / "storage/product-images/a.jpg").read_bytes())
            self.assertEqual(b"document-content", (staging / "storage/supplier-documents/x.pdf").read_bytes())
            self.assertFalse((staging / "manifest.json").exists())


class SqlQuotingTest(unittest.TestCase):
    def test_identifier_quoting(self):
        self.assertEqual('"db""name"', restore.sql_identifier('db"name'))

    def test_literal_quoting(self):
        self.assertEqual("'db''name'", restore.sql_literal("db'name"))


if __name__ == "__main__":
    unittest.main()
