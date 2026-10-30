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

normalize_backup() {
  case "$1" in
    /data/backups/*) printf '%s\n' "$1" ;;
    */*) echo "Informe somente o nome do .spbackup localizado no volume de backups." >&2; exit 2 ;;
    *) printf '/data/backups/%s\n' "$1" ;;
  esac
}

db_name() { sed -n 's/^DB_NAME=//p' "$ENV" | tail -n1; }
confirm_db() {
  target=$(db_name); echo "Banco alvo: $target"; printf 'Digite exatamente o nome do banco para confirmar: '; IFS= read -r typed
  [ "$typed" = "$target" ] || { echo "Confirmação incorreta. Nada foi alterado." >&2; exit 2; }
}

cmd=${1:-help}
case "$cmd" in
  list)
    compose run --rm --no-deps --entrypoint sh restore -c 'for f in /data/backups/*.spbackup; do [ -f "$f" ] && ls -lh "$f"; done'
    ;;
  inspect)
    [ "$#" -eq 2 ] || { echo "Uso: $0 inspect arquivo.spbackup" >&2; exit 2; }
    compose run --rm --no-deps restore inspect "$(normalize_backup "$2")"
    ;;
  restore)
    [ "$#" -eq 2 ] || { echo "Uso: $0 restore arquivo.spbackup" >&2; exit 2; }
    package=$(normalize_backup "$2")
    compose run --rm --no-deps restore inspect "$package"
    confirm_db; target=$(db_name)
    docker compose -f "$MAIN" --env-file "$ENV" stop backend
    set +e; compose run --rm restore restore "$package" --confirm "$target"; rc=$?; set -e
    if [ "$rc" -ne 0 ]; then echo "RESTORE FALHOU. Backend permanecerá parado." >&2; exit "$rc"; fi
    docker compose -f "$MAIN" --env-file "$ENV" up -d --force-recreate backend
    echo "RESTORE_RUNNER=OK"
    ;;
  *)
    echo "Uso: $0 {list|inspect arquivo.spbackup|restore arquivo.spbackup}"
    ;;
esac
