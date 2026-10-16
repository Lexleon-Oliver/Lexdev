import importlib.util
import os
import tempfile
import unittest
import sys
from pathlib import Path


MODULE_PATH = (
    Path(__file__).resolve().parents[1]
    / "systempro_recovery.py"
)

spec = importlib.util.spec_from_file_location(
    "systempro_recovery",
    MODULE_PATH,
)
recovery = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = recovery
assert spec.loader is not None
spec.loader.exec_module(recovery)


PASSWORD = "senha-de-suporte-forte-123"


class RecoveryKitTest(unittest.TestCase):
    def build_envelope(self, instance_files=None):
        with tempfile.TemporaryDirectory() as temp:
            instance_dir = Path(temp) / "instance"
            instance_dir.mkdir()

            for relative, content in (instance_files or {}).items():
                target = instance_dir / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(content)

            kit_id = "c971c4bf-a714-4d3e-b58c-cec434f8fe9e"
            created_at = "2026-10-16T03:30:00.000000Z"

            payload = recovery.build_payload(
                "fiscal-key-test-value",
                instance_dir,
                kit_id,
                created_at,
            )
            header = recovery.build_header(
                kit_id,
                created_at,
                b"0123456789abcdef",
                b"123456789012",
            )

            return recovery.encrypt_payload(
                payload,
                PASSWORD,
                header,
            )

    def test_round_trip_preserves_fiscal_key(self):
        envelope = self.build_envelope()
        header, payload = recovery.decrypt_payload(
            envelope,
            PASSWORD,
        )

        self.assertEqual(1, header["formatVersion"])

        fiscal = next(
            item
            for item in payload["materials"]
            if item["path"] == recovery.ENV_SECRET_PATH
        )

        self.assertEqual(
            b"fiscal-key-test-value",
            recovery.b64url_decode(fiscal["content"]),
        )

    def test_wrong_password_is_rejected(self):
        envelope = self.build_envelope()

        with self.assertRaises(recovery.RecoveryKitError):
            recovery.decrypt_payload(
                envelope,
                "outra-senha-de-suporte-456",
            )

    def test_ciphertext_tamper_is_rejected(self):
        envelope = bytearray(self.build_envelope())
        envelope[-1] ^= 0x01

        with self.assertRaises(recovery.RecoveryKitError):
            recovery.decrypt_payload(
                bytes(envelope),
                PASSWORD,
            )

    def test_instance_files_are_included(self):
        envelope = self.build_envelope(
            {
                "instance.json": b'{"installationId":"abc"}',
                "keys/installation-key.json": b'{"private":"secret"}',
            }
        )
        _, payload = recovery.decrypt_payload(
            envelope,
            PASSWORD,
        )

        paths = {
            item["path"]
            for item in payload["materials"]
        }

        self.assertIn(
            "systempro-instance/instance.json",
            paths,
        )
        self.assertIn(
            "systempro-instance/keys/installation-key.json",
            paths,
        )

    def test_instance_symlink_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            instance_dir = Path(temp) / "instance"
            instance_dir.mkdir()
            target = Path(temp) / "outside"
            target.write_text("secret")
            (instance_dir / "link").symlink_to(target)

            with self.assertRaises(recovery.RecoveryKitError):
                recovery.build_payload(
                    "fiscal",
                    instance_dir,
                    "c971c4bf-a714-4d3e-b58c-cec434f8fe9e",
                    "2026-10-16T03:30:00.000000Z",
                )

    def test_empty_fiscal_key_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(recovery.RecoveryKitError):
                recovery.build_payload(
                    "",
                    Path(temp),
                    "c971c4bf-a714-4d3e-b58c-cec434f8fe9e",
                    "2026-10-16T03:30:00.000000Z",
                )

    def test_unsafe_material_path_is_rejected(self):
        with self.assertRaises(recovery.RecoveryKitError):
            recovery.add_material(
                [],
                "../outside",
                b"secret",
                "critical-secret",
            )


if __name__ == "__main__":
    unittest.main()
