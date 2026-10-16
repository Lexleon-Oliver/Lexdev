#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import time
import uuid
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any, Iterable

FORMAT_VERSION = 1
DATABASE_ENTRY = "database/systempro.dump"
MANIFEST_ENTRY = "manifest.json"
CHECKSUMS_ENTRY = "checksums.sha256"
PRODUCT_PREFIX = "storage/product-images/"
DOCUMENTS_PREFIX = "storage/supplier-documents/"
REQUIRED_COMPONENTS = {"database", "product-images", "supplier-documents"}
METADATA_ENTRIES = {MANIFEST_ENTRY, CHECKSUMS_ENTRY}


class RestoreError(RuntimeError):
    pass


@dataclass(frozen=True)
class RestoreConfig:
    db_host: str
    db_port: int
    db_name: str
    db_user: str
    db_password: str
    maintenance_db: str
    product_images_path: Path
    documents_path: Path
    work_root: Path
    storage_uid: int
    storage_gid: int
    max_uncompressed_bytes: int
    max_entries: int
    command_timeout_seconds: int

    @staticmethod
    def from_env() -> "RestoreConfig":
        required = {
            "DB_HOST": os.getenv("DB_HOST", "").strip(),
            "DB_NAME": os.getenv("DB_NAME", "").strip(),
            "DB_USER": os.getenv("DB_USER", "").strip(),
            "DB_PASSWORD": os.getenv("DB_PASSWORD", ""),
        }
        missing = [name for name, value in required.items() if not value]
        if missing:
            raise RestoreError(
                "Configuração obrigatória ausente: " + ", ".join(missing)
            )

        try:
            port = int(os.getenv("DB_PORT", "5432"))
            uid = int(os.getenv("RESTORE_STORAGE_UID", "100"))
            gid = int(os.getenv("RESTORE_STORAGE_GID", "101"))
            max_bytes = int(os.getenv("RESTORE_MAX_UNCOMPRESSED_BYTES", str(500 * 1024**3)))
            max_entries = int(os.getenv("RESTORE_MAX_ENTRIES", "1000000"))
            timeout = int(os.getenv("RESTORE_COMMAND_TIMEOUT_SECONDS", "3600"))
        except ValueError as exc:
            raise RestoreError("Configuração numérica inválida para o restore.") from exc

        if not 1 <= port <= 65535:
            raise RestoreError("DB_PORT inválida.")
        if uid < 0 or gid < 0:
            raise RestoreError("UID/GID de storage inválido.")
        if max_bytes <= 0 or max_entries <= 0 or timeout <= 0:
            raise RestoreError("Limites do restore devem ser maiores que zero.")

        return RestoreConfig(
            db_host=required["DB_HOST"],
            db_port=port,
            db_name=required["DB_NAME"],
            db_user=required["DB_USER"],
            db_password=required["DB_PASSWORD"],
            maintenance_db=os.getenv("DB_MAINTENANCE_DB", "postgres").strip() or "postgres",
            product_images_path=Path(os.getenv("PRODUCT_IMAGES_PATH", "/data/product-images")),
            documents_path=Path(os.getenv("DOCUMENTS_PATH", "/data/supplier-documents")),
            work_root=Path(os.getenv("RESTORE_WORK_PATH", "/restore-work")),
            storage_uid=uid,
            storage_gid=gid,
            max_uncompressed_bytes=max_bytes,
            max_entries=max_entries,
            command_timeout_seconds=timeout,
        )


@dataclass(frozen=True)
class PackageInfo:
    package_file: Path
    package_sha256: str
    manifest: dict[str, Any]
    checksums: dict[str, str]
    total_uncompressed_bytes: int
    entry_count: int


@dataclass(frozen=True)
class DatabaseMetadata:
    owner: str


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def log(message: str) -> None:
    print(message, flush=True)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_stream(stream) -> str:
    digest = hashlib.sha256()
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        digest.update(chunk)
    return digest.hexdigest()


def safe_zip_name(name: str) -> PurePosixPath:
    if not name or "\\" in name or "\x00" in name:
        raise RestoreError("Pacote contém caminho inválido.")
    path = PurePosixPath(name)
    if path.is_absolute() or any(part in ("", ".", "..") for part in path.parts):
        raise RestoreError(f"Pacote contém caminho inseguro: {name}")
    return path


def is_symlink_zip_entry(info: zipfile.ZipInfo) -> bool:
    unix_mode = (info.external_attr >> 16) & 0xFFFF
    return stat.S_ISLNK(unix_mode)


def allowed_payload_name(name: str) -> bool:
    if name == DATABASE_ENTRY:
        return True
    if name.startswith(PRODUCT_PREFIX) and len(name) > len(PRODUCT_PREFIX):
        return True
    if name.startswith(DOCUMENTS_PREFIX) and len(name) > len(DOCUMENTS_PREFIX):
        return True
    return False


def parse_checksums(content: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw_line in content.splitlines():
        if not raw_line.strip():
            continue
        if len(raw_line) < 67 or raw_line[64:66] != "  ":
            raise RestoreError("Linha inválida em checksums.sha256.")
        checksum = raw_line[:64]
        entry = raw_line[66:]
        if any(c not in "0123456789abcdefABCDEF" for c in checksum):
            raise RestoreError("SHA-256 inválido em checksums.sha256.")
        safe_zip_name(entry)
        if not allowed_payload_name(entry):
            raise RestoreError(f"Payload não permitido no pacote: {entry}")
        if entry in result:
            raise RestoreError(f"Entrada duplicada em checksums.sha256: {entry}")
        result[entry] = checksum.lower()
    if DATABASE_ENTRY not in result:
        raise RestoreError("checksums.sha256 não referencia database/systempro.dump.")
    return result


def validate_manifest(manifest: dict[str, Any]) -> None:
    if manifest.get("formatVersion") != FORMAT_VERSION:
        raise RestoreError(
            f"Versão de formato não suportada: {manifest.get('formatVersion')!r}. "
            f"Suportada: {FORMAT_VERSION}."
        )
    engine = str(manifest.get("databaseEngine", "")).strip().lower()
    if engine not in {"postgresql", "postgres"}:
        raise RestoreError("O pacote não contém um backup PostgreSQL suportado.")
    if str(manifest.get("storageProvider", "")).strip().lower() != "local":
        raise RestoreError("Restore v1 suporta apenas storageProvider=local.")
    database_name = manifest.get("databaseName")
    if not isinstance(database_name, str) or not database_name.strip():
        raise RestoreError("manifest.json não contém databaseName válido.")
    components = manifest.get("includedComponents")
    if not isinstance(components, list) or not REQUIRED_COMPONENTS.issubset(set(components)):
        raise RestoreError("manifest.json não declara todos os componentes obrigatórios.")


def inspect_package(package_file: Path, max_bytes: int, max_entries: int) -> PackageInfo:
    package_file = package_file.resolve()
    if not package_file.is_file():
        raise RestoreError(f"Backup não encontrado: {package_file}")
    if package_file.suffix != ".spbackup":
        raise RestoreError("O arquivo de restore deve usar a extensão .spbackup.")

    try:
        with zipfile.ZipFile(package_file, "r") as archive:
            infos = archive.infolist()
            if len(infos) > max_entries:
                raise RestoreError("Pacote excede o limite de entradas permitido.")

            names: set[str] = set()
            payload_names: set[str] = set()
            total_uncompressed = 0
            for info in infos:
                safe_zip_name(info.filename.rstrip("/"))
                if info.filename in names:
                    raise RestoreError(f"Entrada ZIP duplicada: {info.filename}")
                names.add(info.filename)
                if is_symlink_zip_entry(info):
                    raise RestoreError(f"Links simbólicos não são permitidos no pacote: {info.filename}")
                total_uncompressed += info.file_size
                if total_uncompressed > max_bytes:
                    raise RestoreError("Pacote excede o limite máximo descompactado.")
                if not info.is_dir() and info.filename not in METADATA_ENTRIES:
                    if not allowed_payload_name(info.filename):
                        raise RestoreError(f"Payload não permitido no pacote: {info.filename}")
                    payload_names.add(info.filename)

            for required in (MANIFEST_ENTRY, CHECKSUMS_ENTRY, DATABASE_ENTRY):
                if required not in names:
                    raise RestoreError(f"Entrada obrigatória ausente: {required}")

            manifest_info = archive.getinfo(MANIFEST_ENTRY)
            checksum_info = archive.getinfo(CHECKSUMS_ENTRY)
            if manifest_info.file_size > 1024 * 1024 or checksum_info.file_size > 64 * 1024 * 1024:
                raise RestoreError("Metadados do pacote excedem o tamanho permitido.")

            try:
                manifest = json.loads(archive.read(MANIFEST_ENTRY).decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                raise RestoreError("manifest.json inválido.") from exc
            if not isinstance(manifest, dict):
                raise RestoreError("manifest.json deve conter um objeto JSON.")
            validate_manifest(manifest)

            try:
                checksum_text = archive.read(CHECKSUMS_ENTRY).decode("utf-8")
            except UnicodeDecodeError as exc:
                raise RestoreError("checksums.sha256 não é UTF-8 válido.") from exc
            checksums = parse_checksums(checksum_text)

            if payload_names != set(checksums):
                raise RestoreError("Conteúdo do pacote não corresponde integralmente ao checksums.sha256.")

            for entry, expected in checksums.items():
                with archive.open(entry, "r") as stream:
                    actual = sha256_stream(stream)
                if actual.lower() != expected.lower():
                    raise RestoreError(f"Checksum inválido para: {entry}")

    except zipfile.BadZipFile as exc:
        raise RestoreError("Arquivo .spbackup não é um ZIP válido.") from exc

    return PackageInfo(
        package_file=package_file,
        package_sha256=sha256_file(package_file),
        manifest=manifest,
        checksums=checksums,
        total_uncompressed_bytes=total_uncompressed,
        entry_count=len(infos),
    )


def extract_payload(info: PackageInfo, destination: Path) -> None:
    destination.mkdir(parents=True, exist_ok=False)
    with zipfile.ZipFile(info.package_file, "r") as archive:
        for zip_info in archive.infolist():
            if zip_info.is_dir() or zip_info.filename in METADATA_ENTRIES:
                continue
            relative = safe_zip_name(zip_info.filename)
            target = (destination / Path(*relative.parts)).resolve()
            if destination.resolve() not in target.parents:
                raise RestoreError("Tentativa de extração fora do staging.")
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(zip_info, "r") as source, target.open("wb") as output:
                shutil.copyfileobj(source, output, length=1024 * 1024)

    (destination / "storage" / "product-images").mkdir(parents=True, exist_ok=True)
    (destination / "storage" / "supplier-documents").mkdir(parents=True, exist_ok=True)


def command_env(config: RestoreConfig) -> dict[str, str]:
    env = os.environ.copy()
    env["PGPASSWORD"] = config.db_password
    return env


def run_command(
    command: list[str],
    config: RestoreConfig,
    *,
    timeout: int | None = None,
    capture: bool = True,
) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            command,
            check=True,
            text=True,
            stdout=subprocess.PIPE if capture else None,
            stderr=subprocess.PIPE if capture else None,
            env=command_env(config),
            timeout=timeout or config.command_timeout_seconds,
        )
    except subprocess.TimeoutExpired as exc:
        raise RestoreError(f"Comando excedeu o timeout: {command[0]}") from exc
    except subprocess.CalledProcessError as exc:
        detail = (exc.stderr or "").strip()
        if len(detail) > 1200:
            detail = detail[:1200] + "..."
        raise RestoreError(
            f"Falha ao executar {command[0]} (exit={exc.returncode})"
            + (f": {detail}" if detail else ".")
        ) from exc
    except OSError as exc:
        raise RestoreError(f"Não foi possível executar {command[0]}: {exc}") from exc


def psql(config: RestoreConfig, database: str, sql: str) -> str:
    result = run_command(
        [
            "psql",
            "--no-psqlrc",
            "--host", config.db_host,
            "--port", str(config.db_port),
            "--username", config.db_user,
            "--dbname", database,
            "--set", "ON_ERROR_STOP=1",
            "--tuples-only",
            "--no-align",
            "--command", sql,
        ],
        config,
    )
    return result.stdout.strip()


def sql_literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def sql_identifier(value: str) -> str:
    if "\x00" in value:
        raise RestoreError("Identificador SQL inválido.")
    return '"' + value.replace('"', '""') + '"'


def database_metadata(config: RestoreConfig) -> DatabaseMetadata:
    owner = psql(
        config,
        config.maintenance_db,
        "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = "
        + sql_literal(config.db_name)
        + ";",
    )
    if not owner:
        raise RestoreError(f"Banco alvo não existe: {config.db_name}")
    return DatabaseMetadata(owner=owner.splitlines()[0].strip())


def active_connection_count(config: RestoreConfig) -> int:
    value = psql(
        config,
        config.maintenance_db,
        "SELECT count(*) FROM pg_stat_activity WHERE datname = "
        + sql_literal(config.db_name)
        + " AND pid <> pg_backend_pid();",
    )
    try:
        return int(value)
    except ValueError as exc:
        raise RestoreError("Não foi possível determinar conexões ativas no PostgreSQL.") from exc


def require_no_active_connections(config: RestoreConfig) -> None:
    count = active_connection_count(config)
    if count != 0:
        raise RestoreError(
            f"Restore recusado: existem {count} conexão(ões) ativa(s) no banco {config.db_name}. "
            "Pare o backend/serviços que usam o banco antes do restore."
        )


def verify_dump_archive(config: RestoreConfig, dump_file: Path) -> None:
    if not dump_file.is_file() or dump_file.stat().st_size == 0:
        raise RestoreError("Dump PostgreSQL ausente ou vazio.")
    run_command(["pg_restore", "--list", str(dump_file)], config)


def create_database(config: RestoreConfig, database: str, owner: str) -> None:
    psql(
        config,
        config.maintenance_db,
        f"CREATE DATABASE {sql_identifier(database)} OWNER {sql_identifier(owner)} TEMPLATE template0;",
    )


def drop_database(config: RestoreConfig, database: str) -> None:
    psql(
        config,
        config.maintenance_db,
        f"DROP DATABASE IF EXISTS {sql_identifier(database)} WITH (FORCE);",
    )


def restore_dump(config: RestoreConfig, database: str, dump_file: Path) -> None:
    run_command(
        [
            "pg_restore",
            "--exit-on-error",
            "--no-owner",
            "--no-privileges",
            "--host", config.db_host,
            "--port", str(config.db_port),
            "--username", config.db_user,
            "--dbname", database,
            str(dump_file),
        ],
        config,
        capture=True,
    )


def validate_restored_database(config: RestoreConfig, database: str) -> None:
    exists = psql(
        config,
        database,
        "SELECT CASE WHEN to_regclass('public.flyway_schema_history') IS NULL THEN '0' ELSE '1' END;",
    )
    if exists.strip() != "1":
        raise RestoreError("Banco restaurado não contém public.flyway_schema_history.")


def proof_restore_to_temporary_database(
    config: RestoreConfig,
    dump_file: Path,
    owner: str,
) -> None:
    temp_name = "sp_restore_verify_" + uuid.uuid4().hex[:12]
    log(f"[preflight] Restore-prova em banco temporário: {temp_name}")
    try:
        create_database(config, temp_name, owner)
        restore_dump(config, temp_name, dump_file)
        validate_restored_database(config, temp_name)
    finally:
        try:
            drop_database(config, temp_name)
        except RestoreError as exc:
            log(f"[aviso] Não foi possível remover banco temporário {temp_name}: {exc}")


def dump_current_database(config: RestoreConfig, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    run_command(
        [
            "pg_dump",
            "--format=custom",
            "--no-owner",
            "--no-privileges",
            "--host", config.db_host,
            "--port", str(config.db_port),
            "--username", config.db_user,
            "--file", str(output),
            config.db_name,
        ],
        config,
        capture=True,
    )
    verify_dump_archive(config, output)


def assert_safe_storage_root(path: Path) -> Path:
    original = path.absolute()
    if original.exists() and original.is_symlink():
        raise RestoreError(f"Storage root não pode ser link simbólico: {original}")
    original.mkdir(parents=True, exist_ok=True)
    path = original.resolve()
    if path == Path("/"):
        raise RestoreError("Storage root não pode ser /.")
    if not path.is_dir():
        raise RestoreError(f"Storage root inválido: {path}")
    return path


def ensure_no_symlinks(root: Path) -> None:
    for current, directories, files in os.walk(root, followlinks=False):
        current_path = Path(current)
        for name in directories + files:
            candidate = current_path / name
            if candidate.is_symlink():
                raise RestoreError(f"Storage contém link simbólico não suportado: {candidate}")


def directory_file_bytes(root: Path) -> int:
    total = 0
    for current, _, files in os.walk(root, followlinks=False):
        current_path = Path(current)
        for name in files:
            candidate = current_path / name
            if candidate.is_symlink():
                raise RestoreError(f"Storage contém link simbólico não suportado: {candidate}")
            total += candidate.stat().st_size
    return total


def current_database_size(config: RestoreConfig) -> int:
    value = psql(
        config,
        config.maintenance_db,
        "SELECT pg_database_size(" + sql_literal(config.db_name) + ");",
    )
    try:
        size = int(value)
    except ValueError as exc:
        raise RestoreError("Não foi possível determinar o tamanho do banco alvo.") from exc
    if size < 0:
        raise RestoreError("PostgreSQL retornou tamanho inválido para o banco alvo.")
    return size


def snapshot_directory(root: Path, archive_file: Path) -> None:
    root = assert_safe_storage_root(root)
    ensure_no_symlinks(root)
    archive_file.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive_file, "w:gz", format=tarfile.PAX_FORMAT) as archive:
        for child in sorted(root.iterdir(), key=lambda p: p.name):
            archive.add(child, arcname=child.name, recursive=True)


def clear_directory(root: Path) -> None:
    root = assert_safe_storage_root(root)
    for child in list(root.iterdir()):
        if child.is_symlink() or child.is_file():
            child.unlink()
        elif child.is_dir():
            shutil.rmtree(child)
        else:
            child.unlink(missing_ok=True)


def chown_tree(root: Path, uid: int, gid: int) -> None:
    os.chown(root, uid, gid)
    for current, directories, files in os.walk(root, followlinks=False):
        current_path = Path(current)
        for name in directories + files:
            candidate = current_path / name
            if candidate.is_symlink():
                raise RestoreError(f"Link simbólico inesperado durante chown: {candidate}")
            os.chown(candidate, uid, gid)


def copy_directory_contents(source: Path, target: Path, uid: int, gid: int) -> None:
    target = assert_safe_storage_root(target)
    clear_directory(target)
    for child in sorted(source.iterdir(), key=lambda p: p.name):
        destination = target / child.name
        if child.is_dir():
            shutil.copytree(child, destination, copy_function=shutil.copy2)
        elif child.is_file():
            shutil.copy2(child, destination)
        else:
            raise RestoreError(f"Tipo de arquivo não suportado no staging: {child}")
    chown_tree(target, uid, gid)


def safe_extract_tar(archive_file: Path, target: Path) -> None:
    target = assert_safe_storage_root(target)
    clear_directory(target)
    with tarfile.open(archive_file, "r:gz") as archive:
        members = archive.getmembers()
        for member in members:
            path = PurePosixPath(member.name)
            if path.is_absolute() or any(part in ("", ".", "..") for part in path.parts):
                raise RestoreError("Snapshot de rollback contém caminho inseguro.")
            if member.issym() or member.islnk():
                raise RestoreError("Snapshot de rollback contém link não suportado.")
        archive.extractall(target, filter="data")


def verify_restored_storage(
    info: PackageInfo,
    product_root: Path,
    documents_root: Path,
) -> None:
    expected_files: set[Path] = set()
    for entry, expected_hash in info.checksums.items():
        if entry == DATABASE_ENTRY:
            continue
        if entry.startswith(PRODUCT_PREFIX):
            relative = entry[len(PRODUCT_PREFIX):]
            target = product_root / Path(*PurePosixPath(relative).parts)
        elif entry.startswith(DOCUMENTS_PREFIX):
            relative = entry[len(DOCUMENTS_PREFIX):]
            target = documents_root / Path(*PurePosixPath(relative).parts)
        else:
            raise RestoreError(f"Entrada de storage inesperada: {entry}")
        if not target.is_file():
            raise RestoreError(f"Arquivo restaurado ausente: {entry}")
        if sha256_file(target).lower() != expected_hash.lower():
            raise RestoreError(f"Checksum pós-restore inválido: {entry}")
        expected_files.add(target.resolve())

    actual_files: set[Path] = set()
    for root in (product_root, documents_root):
        for current, _, files in os.walk(root, followlinks=False):
            for name in files:
                actual_files.add((Path(current) / name).resolve())
    if actual_files != expected_files:
        raise RestoreError("Storage restaurado contém arquivos ausentes ou extras.")


def write_json_atomic(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    os.replace(temp, path)


def update_journal(path: Path, **changes: Any) -> dict[str, Any]:
    journal: dict[str, Any] = {}
    if path.exists():
        journal = json.loads(path.read_text(encoding="utf-8"))
    journal.update(changes)
    journal["updatedAt"] = utc_now()
    write_json_atomic(path, journal)
    return journal


def ensure_free_space(work_root: Path, required_bytes: int) -> None:
    free = shutil.disk_usage(work_root).free
    reserve = 128 * 1024 * 1024
    if free < required_bytes + reserve:
        raise RestoreError(
            "Espaço livre insuficiente no RESTORE_WORK_PATH. "
            f"Livre={free} requerido~={required_bytes + reserve}."
        )


def replace_database_from_dump(
    config: RestoreConfig,
    dump_file: Path,
    owner: str,
) -> None:
    drop_database(config, config.db_name)
    create_database(config, config.db_name, owner)
    restore_dump(config, config.db_name, dump_file)
    validate_restored_database(config, config.db_name)


def rollback_from_artifacts(
    config: RestoreConfig,
    operation_dir: Path,
    owner: str,
) -> None:
    rollback_dir = operation_dir / "rollback"
    db_dump = rollback_dir / "database-current.dump"
    product_tar = rollback_dir / "product-images-current.tar.gz"
    documents_tar = rollback_dir / "supplier-documents-current.tar.gz"

    for required in (db_dump, product_tar, documents_tar):
        if not required.is_file():
            raise RestoreError(f"Artefato de rollback ausente: {required}")

    log("[rollback] Restaurando banco anterior...")
    replace_database_from_dump(config, db_dump, owner)
    log("[rollback] Restaurando product-images anterior...")
    safe_extract_tar(product_tar, config.product_images_path)
    chown_tree(config.product_images_path, config.storage_uid, config.storage_gid)
    log("[rollback] Restaurando supplier-documents anterior...")
    safe_extract_tar(documents_tar, config.documents_path)
    chown_tree(config.documents_path, config.storage_uid, config.storage_gid)


def restore_command(args: argparse.Namespace) -> int:
    config = RestoreConfig.from_env()
    if args.confirm != config.db_name:
        raise RestoreError(
            "Confirmação inválida. Use --confirm com o nome exato de DB_NAME."
        )

    config.work_root.mkdir(parents=True, exist_ok=True)
    if not config.work_root.is_dir():
        raise RestoreError("RESTORE_WORK_PATH inválido.")

    product_root = assert_safe_storage_root(config.product_images_path)
    documents_root = assert_safe_storage_root(config.documents_path)
    if product_root == documents_root:
        raise RestoreError("PRODUCT_IMAGES_PATH e DOCUMENTS_PATH não podem apontar para o mesmo diretório.")

    log("[1/10] Validando pacote .spbackup e checksums...")
    info = inspect_package(
        Path(args.package),
        config.max_uncompressed_bytes,
        config.max_entries,
    )
    log(
        f"       formatVersion={info.manifest['formatVersion']} "
        f"sourceDatabase={info.manifest['databaseName']} "
        f"sha256={info.package_sha256}"
    )

    operation_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
    operation_dir = config.work_root / f"restore-{operation_id}"
    staging_dir = operation_dir / "staging"
    rollback_dir = operation_dir / "rollback"
    journal_path = operation_dir / "restore-journal.json"
    operation_dir.mkdir(parents=True, exist_ok=False)

    update_journal(
        journal_path,
        formatVersion=1,
        operationId=operation_id,
        status="VALIDATING",
        startedAt=utc_now(),
        backupFile=str(info.package_file),
        backupSha256=info.package_sha256,
        sourceDatabase=info.manifest["databaseName"],
        targetDatabase=config.db_name,
        productImagesPath=str(product_root),
        documentsPath=str(documents_root),
    )

    log("[2/10] Extraindo payload em staging seguro...")
    ensure_free_space(config.work_root, info.total_uncompressed_bytes)
    extract_payload(info, staging_dir)
    dump_file = staging_dir / DATABASE_ENTRY
    verify_dump_archive(config, dump_file)

    log("[3/10] Validando conexão PostgreSQL, banco alvo e espaço para rollback...")
    metadata = database_metadata(config)
    database_bytes = current_database_size(config)
    storage_bytes = directory_file_bytes(product_root) + directory_file_bytes(documents_root)
    ensure_free_space(config.work_root, database_bytes + storage_bytes)
    update_journal(
        journal_path,
        databaseOwner=metadata.owner,
        estimatedCurrentDatabaseBytes=database_bytes,
        currentStorageBytes=storage_bytes,
    )

    log("[4/10] Executando restore-prova do dump em banco temporário...")
    proof_restore_to_temporary_database(config, dump_file, metadata.owner)

    log("[5/10] Confirmando modo de manutenção (zero conexões no banco alvo)...")
    require_no_active_connections(config)

    log("[6/10] Criando artefatos persistentes de rollback...")
    rollback_dir.mkdir(parents=True, exist_ok=False)
    rollback_db = rollback_dir / "database-current.dump"
    dump_current_database(config, rollback_db)
    snapshot_directory(product_root, rollback_dir / "product-images-current.tar.gz")
    snapshot_directory(documents_root, rollback_dir / "supplier-documents-current.tar.gz")
    update_journal(
        journal_path,
        status="PREPARED",
        rollbackDatabase=str(rollback_db),
        rollbackProductImages=str(rollback_dir / "product-images-current.tar.gz"),
        rollbackDocuments=str(rollback_dir / "supplier-documents-current.tar.gz"),
    )

    # A segunda checagem fecha a janela entre o dump de rollback e a mutação.
    require_no_active_connections(config)

    mutation_started = False
    try:
        log("[7/10] Substituindo PostgreSQL pelo dump validado...")
        mutation_started = True
        replace_database_from_dump(config, dump_file, metadata.owner)
        update_journal(journal_path, status="DATABASE_RESTORED")

        log("[8/10] Restaurando product-images e supplier-documents...")
        copy_directory_contents(
            staging_dir / "storage" / "product-images",
            product_root,
            config.storage_uid,
            config.storage_gid,
        )
        copy_directory_contents(
            staging_dir / "storage" / "supplier-documents",
            documents_root,
            config.storage_uid,
            config.storage_gid,
        )
        update_journal(journal_path, status="STORAGE_RESTORED")

        log("[9/10] Validando banco e checksums no destino restaurado...")
        validate_restored_database(config, config.db_name)
        verify_restored_storage(info, product_root, documents_root)

        # O backup original já está preservado; staging não é necessário após validação final.
        shutil.rmtree(staging_dir, ignore_errors=True)
        update_journal(
            journal_path,
            status="COMPLETE",
            completedAt=utc_now(),
            stagingRemoved=True,
        )
        log("[10/10] RESTORE=OK")
        log(f"         Journal: {journal_path}")
        log(f"         Rollback preservado: {rollback_dir}")
        log(
            "         Se a validação funcional posterior falhar, execute o comando rollback "
            "antes de remover estes artefatos."
        )
        return 0
    except Exception as original:
        if not mutation_started:
            raise
        log(f"[falha] Restore falhou após início da mutação: {original}")
        update_journal(journal_path, status="ROLLBACK_RUNNING", failure=str(original)[:1200])
        try:
            log("[rollback] Iniciando rollback automático completo...")
            rollback_from_artifacts(config, operation_dir, metadata.owner)
            update_journal(journal_path, status="ROLLED_BACK", rolledBackAt=utc_now())
            raise RestoreError(
                "Restore falhou, mas o rollback automático foi concluído. "
                f"Consulte {journal_path}. Causa original: {original}"
            ) from original
        except RestoreError as rollback_error:
            # Se este RestoreError é a mensagem de rollback concluído, preserve-a.
            if "rollback automático foi concluído" in str(rollback_error):
                raise
            update_journal(
                journal_path,
                status="ROLLBACK_FAILED",
                rollbackFailure=str(rollback_error)[:1200],
            )
            raise RestoreError(
                "CRÍTICO: restore falhou e o rollback automático também falhou. "
                f"Artefatos preservados em {rollback_dir}. "
                f"Causa restore: {original}. Causa rollback: {rollback_error}"
            ) from rollback_error


def inspect_command(args: argparse.Namespace) -> int:
    max_bytes = int(os.getenv("RESTORE_MAX_UNCOMPRESSED_BYTES", str(500 * 1024**3)))
    max_entries = int(os.getenv("RESTORE_MAX_ENTRIES", "1000000"))
    info = inspect_package(Path(args.package), max_bytes, max_entries)
    print(json.dumps({
        "backupFile": str(info.package_file),
        "backupSha256": info.package_sha256,
        "formatVersion": info.manifest.get("formatVersion"),
        "databaseEngine": info.manifest.get("databaseEngine"),
        "databaseName": info.manifest.get("databaseName"),
        "storageProvider": info.manifest.get("storageProvider"),
        "includedComponents": info.manifest.get("includedComponents"),
        "trigger": info.manifest.get("trigger"),
        "startedAt": info.manifest.get("startedAt"),
        "databaseDumpCompletedAt": info.manifest.get("databaseDumpCompletedAt"),
        "legacyCompletedAt": info.manifest.get("completedAt"),
        "entryCount": info.entry_count,
        "totalUncompressedBytes": info.total_uncompressed_bytes,
        "payloadCount": len(info.checksums),
        "integrity": "OK",
    }, indent=2, ensure_ascii=False))
    return 0


def rollback_command(args: argparse.Namespace) -> int:
    config = RestoreConfig.from_env()
    if args.confirm != config.db_name:
        raise RestoreError("Confirmação inválida para rollback.")
    operation_dir = Path(args.operation_dir).resolve()
    journal_path = operation_dir / "restore-journal.json"
    if not journal_path.is_file():
        raise RestoreError("restore-journal.json ausente no diretório informado.")
    journal = json.loads(journal_path.read_text(encoding="utf-8"))
    if journal.get("targetDatabase") != config.db_name:
        raise RestoreError("O journal pertence a outro banco alvo.")
    owner = journal.get("databaseOwner")
    if not isinstance(owner, str) or not owner:
        raise RestoreError("Owner do banco ausente no journal.")
    require_no_active_connections(config)
    update_journal(journal_path, status="MANUAL_ROLLBACK_RUNNING")
    rollback_from_artifacts(config, operation_dir, owner)
    update_journal(journal_path, status="MANUALLY_ROLLED_BACK", manuallyRolledBackAt=utc_now())
    log("ROLLBACK=OK")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="SystemPro Restore v1 - restore independente de .spbackup"
    )
    sub = parser.add_subparsers(dest="command", required=True)

    inspect_parser = sub.add_parser("inspect", help="Valida pacote sem acessar o banco")
    inspect_parser.add_argument("package")
    inspect_parser.set_defaults(func=inspect_command)

    restore_parser = sub.add_parser("restore", help="Executa restore completo com rollback")
    restore_parser.add_argument("package")
    restore_parser.add_argument("--confirm", required=True, help="Deve ser exatamente DB_NAME")
    restore_parser.set_defaults(func=restore_command)

    rollback_parser = sub.add_parser("rollback", help="Restaura os artefatos pré-restore")
    rollback_parser.add_argument("operation_dir")
    rollback_parser.add_argument("--confirm", required=True, help="Deve ser exatamente DB_NAME")
    rollback_parser.set_defaults(func=rollback_command)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except RestoreError as exc:
        print(f"ERRO: {exc}", file=sys.stderr, flush=True)
        return 2
    except KeyboardInterrupt:
        print("ERRO: operação interrompida pelo operador.", file=sys.stderr, flush=True)
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
