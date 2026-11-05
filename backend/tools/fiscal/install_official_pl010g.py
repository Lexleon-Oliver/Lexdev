#!/usr/bin/env python3
"""Importacao OFFLINE e conservadora de XSDs oficiais PL_010g v1.03.

Nao baixa arquivos, nao substitui o PL_010f e nao altera o schema padrao do ERP.
O hash identifica os bytes recebidos; isoladamente NAO prova a autoria.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

XSD_NS = "{http://www.w3.org/2001/XMLSchema}"
REQUIRED = {"nfe_v4.00.xsd", "leiauteNFe_v4.00.xsd"}
NEW_ELEMENTS = {"vUnComLiq", "vProdLiq", "vProdLiqTot", "gPgtoVinc"}
MAX_FILE_SIZE = 16 * 1024 * 1024
MAX_TOTAL_SIZE = 100 * 1024 * 1024


class PackageError(ValueError):
    pass


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def validate_zip(source: Path, expected_hash: str) -> dict[str, bytes]:
    actual_hash = sha256_bytes(source.read_bytes())
    if not re.fullmatch(r"[a-fA-F0-9]{64}", expected_hash):
        raise PackageError("SHA-256 esperado deve ter 64 caracteres hexadecimais")
    if actual_hash != expected_hash.lower():
        raise PackageError(f"SHA-256 diverge: obtido {actual_hash}")
    files = {}
    total = 0
    with zipfile.ZipFile(source) as archive:
        for item in archive.infolist():
            if item.is_dir():
                continue
            original = item.filename.replace("\\", "/")
            name = PurePosixPath(original)
            if original.startswith("/") or ".." in name.parts or ":" in original or not name.parts:
                raise PackageError(f"Nome de arquivo inseguro: {original}")
            if (item.external_attr >> 16) & 0o170000 == stat.S_IFLNK:
                raise PackageError(f"Link simbólico não permitido: {original}")
            if name.suffix.lower() != ".xsd":
                continue
            if item.file_size > MAX_FILE_SIZE:
                raise PackageError(f"XSD excessivamente grande: {original}")
            total += item.file_size
            if total > MAX_TOTAL_SIZE:
                raise PackageError("Pacote ultrapassou o limite de extração")
            # Os XSDs do pacote são copiados para um diretório isolado; recusar
            # qualquer ambiguidade de nome, em vez de sobrescrever silenciosamente.
            key = name.name
            if any(existing.casefold() == key.casefold() for existing in files):
                raise PackageError(f"XSD com nome duplicado: {key}")
            data = archive.read(item)
            if len(data) != item.file_size:
                raise PackageError(f"Tamanho inconsistente: {original}")
            files[key] = data
    if not REQUIRED.issubset(files):
        raise PackageError(f"XSDs principais ausentes: {sorted(REQUIRED - files.keys())}")
    check_dependencies(files)
    return files


def check_dependencies(files: dict[str, bytes]) -> None:
    declared = set()
    for name, data in files.items():
        if b"<!DOCTYPE" in data.upper() or b"<!ENTITY" in data.upper():
            raise PackageError(f"DTD/entidades proibidas no XSD: {name}")
        try:
            root = ET.fromstring(data)
        except ET.ParseError as ex:
            raise PackageError(f"XSD ilegível: {name}: {ex}") from ex
        if root.tag != XSD_NS + "schema":
            raise PackageError(f"Arquivo não é um schema XSD: {name}")
        for e in root.iter():
            if e.tag == XSD_NS + "element":
                declared.add(e.get("name"))
            if e.tag in {XSD_NS + "include", XSD_NS + "import", XSD_NS + "redefine", XSD_NS + "override"}:
                location = e.get("schemaLocation")
                if location is None:
                    # xs:import sem schemaLocation é permitido em XML Schema.
                    continue
                if not re.fullmatch(r"[A-Za-z0-9._-]+\.xsd", location, flags=re.I):
                    raise PackageError(f"Dependência não local: {name} -> {location}")
                if location not in files:
                    raise PackageError(f"Dependência ausente: {name} -> {location}")
    missing = NEW_ELEMENTS - declared
    if missing:
        raise PackageError(f"Pacote não contém os campos esperados do 010g: {sorted(missing)}")


def compile_xsds(root: Path, java_source: Path) -> None:
    if not java_source.is_file():
        raise PackageError(f"Validador Java ausente: {java_source}")
    try:
        completed = subprocess.run(
            ["java", str(java_source.resolve()), str((root / "nfe_v4.00.xsd").resolve())],
            capture_output=True, text=True, timeout=45, check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as ex:
        raise PackageError(f"Não foi possível compilar os schemas com Java: {ex}") from ex
    if completed.returncode != 0 or "XSD_COMPILATION=OK" not in completed.stdout:
        raise PackageError("Compilação XSD falhou:\n" + completed.stdout + completed.stderr)


def install(source: Path, expected_hash: str, dest: Path, checker: Path) -> dict:
    if dest.exists():
        raise PackageError(f"Destino já existe: {dest}; não sobrescrevemos schemas instalados")
    files = validate_zip(source, expected_hash)
    dest.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=".pl010g-", dir=dest.parent))
    try:
        for name, data in files.items():
            (staging / name).write_bytes(data)
        compile_xsds(staging, checker)
        manifest = {
            "pacote": "PL_010g_v1.03",
            "origem": "Portal Nacional da NF-e (download obtido e conferido manualmente pelo operador)",
            "zip_sha256": expected_hash.lower(),
            "arquivos_xsd_sha256": {name: sha256_bytes(data) for name, data in sorted(files.items())},
            "nota": "Integridade por SHA-256 não equivale a assinatura/autenticidade do arquivo",
        }
        (staging / "SCHEMA_MANIFEST.json").write_text(
            json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )
        staging.rename(dest)
        return manifest
    finally:
        if staging.exists():
            shutil.rmtree(staging)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zip", dest="zip_path", type=Path, required=True,
                        help="Pacote baixado diretamente do portal oficial, previamente verificado")
    parser.add_argument("--sha256", required=True, help="SHA-256 observado e registrado da origem")
    parser.add_argument("--dest", type=Path, default=Path(__file__).resolve().parents[2] /
                        "src/main/resources/fiscal/nfce/PL_010g")
    args = parser.parse_args()
    try:
        manifest = install(args.zip_path, args.sha256, args.dest,
                           Path(__file__).resolve().with_name("SchemaPackCheck.java"))
    except (PackageError, OSError, zipfile.BadZipFile) as ex:
        parser.exit(2, f"IMPORTACAO=FALHOU: {ex}\n")
    print("IMPORTACAO=OK")
    print("DIRETORIO=" + str(args.dest))
    print("XSDs=" + str(len(manifest["arquivos_xsd_sha256"])))
    print("O schema padrão do ERP permanece inalterado.")


if __name__ == "__main__":
    main()
