#!/bin/sh
set -eu
BASE_DIR=${SYSTEMPRO_HOME:-/opt/systempro}
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
MEDIA_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd -P)
MAIN="$BASE_DIR/docker-compose.yml"
OVERLAY="$MEDIA_DIR/config/docker-compose.maintenance.yml"
ENV="$BASE_DIR/.env"

[ -f "$MAIN" ] && [ -f "$ENV" ] || { echo "Instalação SystemPro não encontrada em $BASE_DIR." >&2; exit 2; }
compose() { docker compose -f "$MAIN" -f "$OVERLAY" --env-file "$ENV" --profile maintenance "$@"; }

command=${1:-help}
case "$command" in
  current)
    compose run --rm --no-deps recovery current
    ;;
  create)
    out=${2:-"$PWD/recovery-kits"}; mkdir -p "$out"; chmod 700 "$out"
    name="systempro-recovery-$(date -u +%Y%m%dT%H%M%SZ).sprecovery"
    abs=$(CDPATH= cd -- "$out" && pwd -P)
    compose run --rm --no-deps --user "$(id -u):$(id -g)" -v "$abs:/recovery-out" recovery create "/recovery-out/$name"
    echo "RECOVERY_KIT_HOST_FILE=$abs/$name"
    ;;
  verify)
    [ "$#" -eq 2 ] || { echo "Uso: $0 verify arquivo.sprecovery" >&2; exit 2; }
    f=$2; [ -f "$f" ] || { echo "Arquivo não encontrado: $f" >&2; exit 2; }
    d=$(CDPATH= cd -- "$(dirname -- "$f")" && pwd -P); n=$(basename -- "$f")
    compose run --rm --no-deps --user "$(id -u):$(id -g)" -v "$d:/recovery-in:ro" recovery verify "/recovery-in/$n"
    ;;
  extract)
    [ "$#" -ge 3 ] || { echo "Uso: $0 extract kit.sprecovery diretorio [--force]" >&2; exit 2; }
    f=$2; out=$3; force=${4:-}; [ -f "$f" ] || { echo "Arquivo não encontrado: $f" >&2; exit 2; }
    mkdir -p "$out"; chmod 700 "$out"
    d=$(CDPATH= cd -- "$(dirname -- "$f")" && pwd -P); n=$(basename -- "$f"); outabs=$(CDPATH= cd -- "$out" && pwd -P)
    compose run --rm --no-deps --user "$(id -u):$(id -g)" -v "$d:/recovery-in:ro" -v "$outabs:/recovery-out" recovery extract "/recovery-in/$n" /recovery-out $force
    ;;
  *)
    echo "Uso: $0 {current|create [dir]|verify kit.sprecovery|extract kit.sprecovery dir [--force]}"
    ;;
esac
