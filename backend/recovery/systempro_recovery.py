#!/usr/bin/env python3

from __future__ import annotations

import argparse
import base64
import getpass
import hashlib
import json
import os
import secrets
import stat
import struct
import sys
import tempfile
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any

from argon2.low_level import Type, hash_secret_raw
from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


MAGIC = b"SYSTEMPRO-SPRECOVERY-V1\n"
FORMAT_VERSION = 1
HEADER_LIMIT = 16 * 1024
FILE_LIMIT = 16 * 1024 * 1024
INSTANCE_FILE_LIMIT = 2 * 1024 * 1024
INSTANCE_TOTAL_LIMIT = 4 * 1024 * 1024

KDF_MEMORY_KIB = 64 * 1024
KDF_TIME_COST = 3
KDF_PARALLELISM = 2
KEY_LENGTH = 32
SALT_LENGTH = 16
NONCE_LENGTH = 12

ENV_SECRET_PATH = "environment/FISCAL_ENCRYPTION_KEY"
INSTANCE_PREFIX = "systempro-instance/"


class RecoveryKitError(RuntimeError):
    pass


@dataclass(frozen=True)
class KdfParameters:
    memory_kib: int
    time_cost: int
    parallelism: int


def utc_now() -> str:
    return (
        datetime.now(timezone.utc)
        .isoformat(timespec="microseconds")
        .replace("+00:00", "Z")
    )


def canonical_json(value: Any) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")


def b64url_encode(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def b64url_decode(value: str) -> bytes:
    if not isinstance(value, str) or not value:
        raise RecoveryKitError("Base64URL inválido.")

    if any(
        char not in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        for char in value
    ):
        raise RecoveryKitError("Base64URL inválido.")

    padding = "=" * ((4 - len(value) % 4) % 4)

    try:
        decoded = base64.b64decode(
            value + padding,
            altchars=b"-_",
            validate=True,
        )
    except Exception as exc:
        raise RecoveryKitError("Base64URL inválido.") from exc

    if b64url_encode(decoded) != value:
        raise RecoveryKitError("Base64URL não canônico.")

    return decoded


def sha256_hex(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def fingerprint(value: bytes) -> str:
    return sha256_hex(value)[:16]


def safe_relative_path(value: str) -> PurePosixPath:
    if not isinstance(value, str) or not value:
        raise RecoveryKitError("Caminho de material inválido.")

    path = PurePosixPath(value)

    if path.is_absolute():
        raise RecoveryKitError("Caminho absoluto não é permitido no Recovery Kit.")

    if any(part in {"", ".", ".."} for part in path.parts):
        raise RecoveryKitError("Caminho inseguro no Recovery Kit.")

    return path


def derive_key(
    passphrase: str,
    salt: bytes,
    params: KdfParameters,
) -> bytes:
    if len(passphrase) < 16:
        raise RecoveryKitError(
            "A senha de suporte deve possuir pelo menos 16 caracteres."
        )

    if not (32 * 1024 <= params.memory_kib <= 256 * 1024):
        raise RecoveryKitError("Parâmetro Argon2id memoryKiB fora do limite seguro.")

    if not (1 <= params.time_cost <= 10):
        raise RecoveryKitError("Parâmetro Argon2id timeCost fora do limite seguro.")

    if not (1 <= params.parallelism <= 8):
        raise RecoveryKitError("Parâmetro Argon2id parallelism fora do limite seguro.")

    if not (16 <= len(salt) <= 64):
        raise RecoveryKitError("Salt Argon2id inválido.")

    return hash_secret_raw(
        secret=passphrase.encode("utf-8"),
        salt=salt,
        time_cost=params.time_cost,
        memory_cost=params.memory_kib,
        parallelism=params.parallelism,
        hash_len=KEY_LENGTH,
        type=Type.ID,
    )


def read_passphrase(confirm: bool) -> str:
    password = getpass.getpass("Senha de suporte do Recovery Kit: ")

    if len(password) < 16:
        raise RecoveryKitError(
            "A senha de suporte deve possuir pelo menos 16 caracteres."
        )

    if confirm:
        repeated = getpass.getpass("Confirme a senha de suporte: ")
        if not secrets.compare_digest(password, repeated):
            raise RecoveryKitError("As senhas informadas não conferem.")

    return password


def add_material(
    materials: list[dict[str, Any]],
    path: str,
    content: bytes,
    kind: str,
) -> None:
    safe_relative_path(path)

    materials.append(
        {
            "path": path,
            "kind": kind,
            "size": len(content),
            "sha256": sha256_hex(content),
            "content": b64url_encode(content),
        }
    )


def collect_instance_materials(
    instance_dir: Path,
    materials: list[dict[str, Any]],
) -> int:
    if not instance_dir.exists():
        return 0

    if instance_dir.is_symlink():
        raise RecoveryKitError(
            "O diretório systempro-instance não pode ser link simbólico."
        )

    if not instance_dir.is_dir():
        raise RecoveryKitError(
            "SYSTEMPRO_INSTANCE_DIR existe, mas não é diretório."
        )

    total = 0
    count = 0

    for root, dirs, files in os.walk(
        instance_dir,
        topdown=True,
        followlinks=False,
    ):
        root_path = Path(root)

        for directory in list(dirs):
            candidate = root_path / directory
            if candidate.is_symlink():
                raise RecoveryKitError(
                    f"Link simbólico não permitido em systempro-instance: {candidate}"
                )

        for filename in sorted(files):
            candidate = root_path / filename

            if candidate.is_symlink():
                raise RecoveryKitError(
                    f"Link simbólico não permitido em systempro-instance: {candidate}"
                )

            file_stat = candidate.stat()

            if not stat.S_ISREG(file_stat.st_mode):
                raise RecoveryKitError(
                    f"Somente arquivos regulares são aceitos: {candidate}"
                )

            if file_stat.st_size > INSTANCE_FILE_LIMIT:
                raise RecoveryKitError(
                    f"Arquivo de identidade excede {INSTANCE_FILE_LIMIT} bytes: {candidate}"
                )

            content = candidate.read_bytes()
            total += len(content)

            if total > INSTANCE_TOTAL_LIMIT:
                raise RecoveryKitError(
                    "O material de identidade excede o limite total permitido."
                )

            relative = candidate.relative_to(instance_dir).as_posix()
            add_material(
                materials,
                INSTANCE_PREFIX + relative,
                content,
                "installation-material",
            )
            count += 1

    return count


def build_payload(
    fiscal_encryption_key: str,
    instance_dir: Path,
    kit_id: str,
    created_at: str,
) -> dict[str, Any]:
    if not fiscal_encryption_key:
        raise RecoveryKitError(
            "FISCAL_ENCRYPTION_KEY não está configurada. "
            "O Recovery Kit não será criado."
        )

    materials: list[dict[str, Any]] = []

    add_material(
        materials,
        ENV_SECRET_PATH,
        fiscal_encryption_key.encode("utf-8"),
        "critical-secret",
    )

    instance_count = collect_instance_materials(
        instance_dir,
        materials,
    )

    materials.sort(key=lambda item: item["path"])

    return {
        "formatVersion": FORMAT_VERSION,
        "kitId": kit_id,
        "createdAt": created_at,
        "application": "SystemPro",
        "materials": materials,
        "summary": {
            "criticalSecrets": 1,
            "installationFiles": instance_count,
        },
    }


def build_header(
    kit_id: str,
    created_at: str,
    salt: bytes,
    nonce: bytes,
) -> dict[str, Any]:
    return {
        "formatVersion": FORMAT_VERSION,
        "kitId": kit_id,
        "createdAt": created_at,
        "cipher": {
            "name": "AES-256-GCM",
            "nonce": b64url_encode(nonce),
        },
        "kdf": {
            "name": "ARGON2ID",
            "salt": b64url_encode(salt),
            "memoryKiB": KDF_MEMORY_KIB,
            "timeCost": KDF_TIME_COST,
            "parallelism": KDF_PARALLELISM,
        },
    }


def encrypt_payload(
    payload: dict[str, Any],
    passphrase: str,
    header: dict[str, Any],
) -> bytes:
    header_bytes = canonical_json(header)
    salt = b64url_decode(header["kdf"]["salt"])
    nonce = b64url_decode(header["cipher"]["nonce"])

    params = KdfParameters(
        memory_kib=int(header["kdf"]["memoryKiB"]),
        time_cost=int(header["kdf"]["timeCost"]),
        parallelism=int(header["kdf"]["parallelism"]),
    )

    key = derive_key(passphrase, salt, params)
    aad = MAGIC + header_bytes

    try:
        ciphertext = AESGCM(key).encrypt(
            nonce,
            canonical_json(payload),
            aad,
        )
    finally:
        key_buffer = bytearray(key)
        for index in range(len(key_buffer)):
            key_buffer[index] = 0

    return (
        MAGIC
        + struct.pack(">I", len(header_bytes))
        + header_bytes
        + ciphertext
    )


def parse_envelope(data: bytes) -> tuple[dict[str, Any], bytes, bytes]:
    if len(data) > FILE_LIMIT:
        raise RecoveryKitError("Recovery Kit excede o tamanho máximo permitido.")

    if not data.startswith(MAGIC):
        raise RecoveryKitError("Arquivo não é um .sprecovery v1 válido.")

    offset = len(MAGIC)

    if len(data) < offset + 4:
        raise RecoveryKitError("Recovery Kit truncado.")

    header_length = struct.unpack(
        ">I",
        data[offset : offset + 4],
    )[0]
    offset += 4

    if header_length <= 0 or header_length > HEADER_LIMIT:
        raise RecoveryKitError("Cabeçalho do Recovery Kit inválido.")

    if len(data) <= offset + header_length:
        raise RecoveryKitError("Recovery Kit sem ciphertext.")

    header_bytes = data[offset : offset + header_length]
    ciphertext = data[offset + header_length :]

    try:
        header = json.loads(header_bytes.decode("utf-8"))
    except Exception as exc:
        raise RecoveryKitError(
            "Cabeçalho JSON do Recovery Kit inválido."
        ) from exc

    if canonical_json(header) != header_bytes:
        raise RecoveryKitError(
            "Cabeçalho do Recovery Kit não está em forma canônica."
        )

    validate_header(header)

    return header, header_bytes, ciphertext


def validate_header(header: dict[str, Any]) -> None:
    if header.get("formatVersion") != FORMAT_VERSION:
        raise RecoveryKitError(
            "Versão de Recovery Kit não suportada."
        )

    if not isinstance(header.get("kitId"), str):
        raise RecoveryKitError("kitId ausente no Recovery Kit.")

    try:
        uuid.UUID(header["kitId"])
    except Exception as exc:
        raise RecoveryKitError("kitId inválido.") from exc

    if not isinstance(header.get("createdAt"), str):
        raise RecoveryKitError("createdAt ausente no Recovery Kit.")

    cipher = header.get("cipher")
    kdf = header.get("kdf")

    if not isinstance(cipher, dict) or cipher.get("name") != "AES-256-GCM":
        raise RecoveryKitError("Cipher do Recovery Kit não suportado.")

    if not isinstance(kdf, dict) or kdf.get("name") != "ARGON2ID":
        raise RecoveryKitError("KDF do Recovery Kit não suportado.")

    nonce = b64url_decode(cipher.get("nonce", ""))
    salt = b64url_decode(kdf.get("salt", ""))

    if len(nonce) != NONCE_LENGTH:
        raise RecoveryKitError("Nonce AES-GCM inválido.")

    params = KdfParameters(
        memory_kib=int(kdf.get("memoryKiB", 0)),
        time_cost=int(kdf.get("timeCost", 0)),
        parallelism=int(kdf.get("parallelism", 0)),
    )

    # Validate limits before Argon2 allocation.
    if not (32 * 1024 <= params.memory_kib <= 256 * 1024):
        raise RecoveryKitError("memoryKiB Argon2id inválido.")

    if not (1 <= params.time_cost <= 10):
        raise RecoveryKitError("timeCost Argon2id inválido.")

    if not (1 <= params.parallelism <= 8):
        raise RecoveryKitError("parallelism Argon2id inválido.")

    if not (16 <= len(salt) <= 64):
        raise RecoveryKitError("Salt Argon2id inválido.")


def decrypt_payload(
    data: bytes,
    passphrase: str,
) -> tuple[dict[str, Any], dict[str, Any]]:
    header, header_bytes, ciphertext = parse_envelope(data)

    kdf = header["kdf"]
    cipher = header["cipher"]

    params = KdfParameters(
        memory_kib=int(kdf["memoryKiB"]),
        time_cost=int(kdf["timeCost"]),
        parallelism=int(kdf["parallelism"]),
    )

    key = derive_key(
        passphrase,
        b64url_decode(kdf["salt"]),
        params,
    )

    try:
        plaintext = AESGCM(key).decrypt(
            b64url_decode(cipher["nonce"]),
            ciphertext,
            MAGIC + header_bytes,
        )
    except InvalidTag as exc:
        raise RecoveryKitError(
            "Senha incorreta ou Recovery Kit adulterado/corrompido."
        ) from exc
    finally:
        key_buffer = bytearray(key)
        for index in range(len(key_buffer)):
            key_buffer[index] = 0

    try:
        payload = json.loads(plaintext.decode("utf-8"))
    except Exception as exc:
        raise RecoveryKitError(
            "Payload descriptografado inválido."
        ) from exc

    validate_payload(payload, header)

    return header, payload


def validate_payload(
    payload: dict[str, Any],
    header: dict[str, Any],
) -> None:
    if payload.get("formatVersion") != FORMAT_VERSION:
        raise RecoveryKitError("Payload com versão inválida.")

    if payload.get("kitId") != header.get("kitId"):
        raise RecoveryKitError("kitId do payload não corresponde ao cabeçalho.")

    if payload.get("createdAt") != header.get("createdAt"):
        raise RecoveryKitError(
            "createdAt do payload não corresponde ao cabeçalho."
        )

    if payload.get("application") != "SystemPro":
        raise RecoveryKitError("Recovery Kit não pertence ao SystemPro.")

    materials = payload.get("materials")

    if not isinstance(materials, list) or not materials:
        raise RecoveryKitError("Recovery Kit não contém materiais.")

    seen: set[str] = set()
    has_fiscal_key = False

    for material in materials:
        if not isinstance(material, dict):
            raise RecoveryKitError("Material inválido no Recovery Kit.")

        path = material.get("path")
        safe_relative_path(path)

        if path in seen:
            raise RecoveryKitError("Material duplicado no Recovery Kit.")
        seen.add(path)

        content = b64url_decode(material.get("content", ""))

        if int(material.get("size", -1)) != len(content):
            raise RecoveryKitError(
                f"Tamanho divergente no material {path}."
            )

        if material.get("sha256") != sha256_hex(content):
            raise RecoveryKitError(
                f"SHA-256 divergente no material {path}."
            )

        if path == ENV_SECRET_PATH:
            has_fiscal_key = True

    if not has_fiscal_key:
        raise RecoveryKitError(
            "Recovery Kit não contém FISCAL_ENCRYPTION_KEY."
        )


def write_atomic(path: Path, data: bytes, mode: int = 0o600) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    os.chmod(path.parent, 0o700)

    fd, temporary_name = tempfile.mkstemp(
        prefix="."+path.name+".",
        dir=str(path.parent),
    )

    try:
        os.fchmod(fd, mode)
        with os.fdopen(fd, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())

        os.replace(temporary_name, path)
        os.chmod(path, mode)
    except Exception:
        try:
            os.unlink(temporary_name)
        except FileNotFoundError:
            pass
        raise


def command_create(args: argparse.Namespace) -> None:
    output = Path(args.output)

    if output.suffix != ".sprecovery":
        raise RecoveryKitError(
            "O arquivo de saída deve possuir extensão .sprecovery."
        )

    if output.exists():
        raise RecoveryKitError(
            "O arquivo de saída já existe. Não será sobrescrito."
        )

    fiscal_key = os.environ.get("FISCAL_ENCRYPTION_KEY", "")
    instance_dir = Path(
        os.environ.get(
            "SYSTEMPRO_INSTANCE_DIR",
            "/data/systempro-instance",
        )
    )

    kit_id = str(uuid.uuid4())
    created_at = utc_now()

    payload = build_payload(
        fiscal_key,
        instance_dir,
        kit_id,
        created_at,
    )

    passphrase = read_passphrase(confirm=True)
    salt = secrets.token_bytes(SALT_LENGTH)
    nonce = secrets.token_bytes(NONCE_LENGTH)

    header = build_header(
        kit_id,
        created_at,
        salt,
        nonce,
    )

    envelope = encrypt_payload(
        payload,
        passphrase,
        header,
    )

    # Self-check before persisting the only copy.
    _, checked_payload = decrypt_payload(
        envelope,
        passphrase,
    )
    validate_payload(checked_payload, header)

    write_atomic(output, envelope, 0o600)

    summary = payload["summary"]

    print("RECOVERY_KIT=CREATED")
    print(f"file={output}")
    print(f"kitId={kit_id}")
    print(f"createdAt={created_at}")
    print(
        "fiscalKeyFingerprint="
        + fingerprint(fiscal_key.encode("utf-8"))
    )
    print(
        "installationFiles="
        + str(summary["installationFiles"])
    )

    if summary["installationFiles"] == 0:
        print(
            "warning=systempro-instance ainda não possui material; "
            "o kit contém a FISCAL_ENCRYPTION_KEY e ficará pronto "
            "para incluir a identidade quando o Host Agent usar esse volume."
        )


def nonsecret_report(
    header: dict[str, Any],
    payload: dict[str, Any],
) -> dict[str, Any]:
    materials = payload["materials"]

    fiscal = next(
        item
        for item in materials
        if item["path"] == ENV_SECRET_PATH
    )
    fiscal_bytes = b64url_decode(fiscal["content"])

    instance = [
        {
            "path": item["path"],
            "size": item["size"],
            "sha256": item["sha256"],
        }
        for item in materials
        if item["path"].startswith(INSTANCE_PREFIX)
    ]

    return {
        "recoveryKit": "OK",
        "formatVersion": header["formatVersion"],
        "kitId": header["kitId"],
        "createdAt": header["createdAt"],
        "cipher": header["cipher"]["name"],
        "kdf": header["kdf"]["name"],
        "fiscalEncryptionKey": {
            "present": True,
            "fingerprint": fingerprint(fiscal_bytes),
        },
        "installationFiles": instance,
    }


def command_verify(args: argparse.Namespace) -> None:
    source = Path(args.file)
    data = source.read_bytes()
    passphrase = read_passphrase(confirm=False)
    header, payload = decrypt_payload(data, passphrase)

    print(
        json.dumps(
            nonsecret_report(header, payload),
            ensure_ascii=False,
            indent=2,
        )
    )


def command_current(args: argparse.Namespace) -> None:
    fiscal_key = os.environ.get("FISCAL_ENCRYPTION_KEY", "")

    if not fiscal_key:
        raise RecoveryKitError(
            "FISCAL_ENCRYPTION_KEY não está configurada."
        )

    instance_dir = Path(
        os.environ.get(
            "SYSTEMPRO_INSTANCE_DIR",
            "/data/systempro-instance",
        )
    )

    materials: list[dict[str, Any]] = []
    instance_count = collect_instance_materials(
        instance_dir,
        materials,
    )

    print("CURRENT_RECOVERY_MATERIAL=OK")
    print(
        "fiscalKeyFingerprint="
        + fingerprint(fiscal_key.encode("utf-8"))
    )
    print(f"installationFiles={instance_count}")


def command_extract(args: argparse.Namespace) -> None:
    source = Path(args.file)
    destination = Path(args.output)

    if destination.exists() and any(destination.iterdir()):
        if not args.force:
            raise RecoveryKitError(
                "Diretório de extração não está vazio. Use --force somente "
                "quando tiver certeza de que pode substituir os arquivos."
            )
    else:
        destination.mkdir(parents=True, exist_ok=True)

    os.chmod(destination, 0o700)

    passphrase = read_passphrase(confirm=False)
    header, payload = decrypt_payload(
        source.read_bytes(),
        passphrase,
    )

    for material in payload["materials"]:
        relative = safe_relative_path(material["path"])
        target = destination.joinpath(*relative.parts)

        try:
            target.resolve().relative_to(destination.resolve())
        except ValueError as exc:
            raise RecoveryKitError(
                "Tentativa de extração fora do diretório alvo."
            ) from exc

        if target.exists() and not args.force:
            raise RecoveryKitError(
                f"Arquivo já existe e não será sobrescrito: {target}"
            )

        content = b64url_decode(material["content"])
        write_atomic(target, content, 0o600)

    report = nonsecret_report(header, payload)
    write_atomic(
        destination / "recovery-manifest.json",
        json.dumps(
            report,
            ensure_ascii=False,
            indent=2,
        ).encode("utf-8")
        + b"\n",
        0o600,
    )

    print("RECOVERY_KIT=EXTRACTED")
    print(f"output={destination}")
    print(f"kitId={header['kitId']}")
    print("secretsPrinted=false")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="SystemPro Recovery Kit v1",
    )

    subparsers = parser.add_subparsers(
        dest="command",
        required=True,
    )

    create = subparsers.add_parser(
        "create",
        help="Cria um .sprecovery criptografado.",
    )
    create.add_argument("output")
    create.set_defaults(handler=command_create)

    verify = subparsers.add_parser(
        "verify",
        help="Descriptografa e valida sem exibir segredos.",
    )
    verify.add_argument("file")
    verify.set_defaults(handler=command_verify)

    current = subparsers.add_parser(
        "current",
        help="Exibe fingerprints do material atual sem segredos.",
    )
    current.set_defaults(handler=command_current)

    extract = subparsers.add_parser(
        "extract",
        help="Extrai materiais recuperados com permissão 0600.",
    )
    extract.add_argument("file")
    extract.add_argument("output")
    extract.add_argument(
        "--force",
        action="store_true",
        help="Permite substituir arquivos já existentes.",
    )
    extract.set_defaults(handler=command_extract)

    return parser


def main() -> int:
    try:
        parser = build_parser()
        args = parser.parse_args()
        args.handler(args)
        return 0
    except RecoveryKitError as exc:
        print(f"ERRO: {exc}", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print("\nERRO: operação cancelada.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
