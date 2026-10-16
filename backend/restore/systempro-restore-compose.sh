#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)

cd "$PROJECT_ROOT"

compose() {
    docker compose --profile maintenance "$@"
}

build_restore() {
    compose build restore
}

normalize_backup() {
    case "$1" in
        /data/backups/*)
            printf '%s\n' "$1"
            ;;
        */*)
            printf '%s\n' "ERRO: informe somente o nome do .spbackup ou /data/backups/<arquivo>." >&2
            exit 2
            ;;
        *)
            printf '/data/backups/%s\n' "$1"
            ;;
    esac
}

normalize_operation() {
    case "$1" in
        /restore-work/restore-*)
            printf '%s\n' "$1"
            ;;
        restore-*)
            printf '/restore-work/%s\n' "$1"
            ;;
        *)
            printf '%s\n' "ERRO: operação inválida. Use restore-YYYYMMDDTHHMMSSZ-XXXXXXXX." >&2
            exit 2
            ;;
    esac
}

target_db_name() {
    compose run --rm --no-deps --entrypoint sh restore \
        -c 'printf "%s\n" "$DB_NAME"' \
        | tail -n 1
}

confirm_database() {
    db_name=$(target_db_name)

    if [ -z "$db_name" ]; then
        printf '%s\n' "ERRO: DB_NAME não foi resolvido pelo Docker Compose." >&2
        exit 2
    fi

    printf 'Banco alvo: %s\n' "$db_name" >&2
    printf 'Digite exatamente o nome do banco para confirmar: ' >&2
    IFS= read -r typed

    if [ "$typed" != "$db_name" ]; then
        printf '%s\n' "ERRO: confirmação não corresponde ao DB_NAME. Nada foi alterado." >&2
        exit 2
    fi

    CONFIRMED_DB_NAME=$db_name
}

list_backups() {
    build_restore
    compose run --rm --no-deps --entrypoint sh restore -c '
        set -eu
        found=0
        for file in /data/backups/*.spbackup; do
            if [ -f "$file" ]; then
                found=1
                ls -lh "$file"
            fi
        done
        if [ "$found" -eq 0 ]; then
            echo "Nenhum .spbackup encontrado em /data/backups."
        fi
    '
}

list_operations() {
    build_restore
    compose run --rm --no-deps --entrypoint sh restore -c '
        set -eu
        found=0
        for dir in /restore-work/restore-*; do
            if [ -d "$dir" ]; then
                found=1
                status="UNKNOWN"
                if [ -f "$dir/restore-journal.json" ]; then
                    status=$(python3 -c '"'"'
import json, sys
with open(sys.argv[1], encoding="utf-8") as stream:
    print(json.load(stream).get("status", "UNKNOWN"))
'"'"' "$dir/restore-journal.json" 2>/dev/null || printf "INVALID_JOURNAL")
                fi
                printf "%s  %s\n" "$(basename "$dir")" "$status"
            fi
        done
        if [ "$found" -eq 0 ]; then
            echo "Nenhuma operação de restore encontrada."
        fi
    '
}

inspect_backup() {
    [ "$#" -eq 1 ] || {
        printf '%s\n' "Uso: $0 inspect <arquivo.spbackup>" >&2
        exit 2
    }

    build_restore
    package=$(normalize_backup "$1")
    compose run --rm --no-deps restore inspect "$package"
}

restore_backup() {
    [ "$#" -eq 1 ] || {
        printf '%s\n' "Uso: $0 restore <arquivo.spbackup>" >&2
        exit 2
    }

    build_restore
    package=$(normalize_backup "$1")

    # A inspeção ZIP/checksum é segura e acontece antes da janela de indisponibilidade.
    printf '%s\n' "== Inspeção segura antes da manutenção =="
    compose run --rm --no-deps restore inspect "$package"

    printf '%s\n' "== Confirmação do banco alvo =="
    confirm_database
    db_name=$CONFIRMED_DB_NAME

    printf '%s\n' "== Parando somente o backend =="
    docker compose stop backend

    printf '%s\n' "== Executando restore completo =="
    set +e
    compose run --rm restore restore "$package" --confirm "$db_name"
    rc=$?
    set -e

    if [ "$rc" -ne 0 ]; then
        printf '%s\n' \
            "RESTORE FALHOU. O backend permanecerá parado. Não execute 'up' até revisar a saída/journal." >&2
        exit "$rc"
    fi

    printf '%s\n' "== Restore concluído; iniciando backend =="
    docker compose up -d --force-recreate backend

    printf '%s\n' "RESTORE_RUNNER=OK"
    printf '%s\n' \
        "Valide login, dados, imagens e documentos antes de remover os artefatos em /restore-work."
}

rollback_operation() {
    [ "$#" -eq 1 ] || {
        printf '%s\n' "Uso: $0 rollback <restore-YYYYMMDDTHHMMSSZ-XXXXXXXX>" >&2
        exit 2
    }

    build_restore
    operation=$(normalize_operation "$1")

    printf '%s\n' "== Confirmação do banco alvo =="
    confirm_database
    db_name=$CONFIRMED_DB_NAME

    printf '%s\n' "== Parando somente o backend =="
    docker compose stop backend

    printf '%s\n' "== Executando rollback =="
    set +e
    compose run --rm restore rollback "$operation" --confirm "$db_name"
    rc=$?
    set -e

    if [ "$rc" -ne 0 ]; then
        printf '%s\n' \
            "ROLLBACK FALHOU. O backend permanecerá parado para evitar agravar o estado." >&2
        exit "$rc"
    fi

    printf '%s\n' "== Rollback concluído; iniciando backend =="
    docker compose up -d --force-recreate backend
    printf '%s\n' "ROLLBACK_RUNNER=OK"
}

usage() {
    cat <<EOF
SystemPro Restore v1 - runner Docker Compose

Uso:
  $0 list
  $0 inspect <arquivo.spbackup>
  $0 restore <arquivo.spbackup>
  $0 operations
  $0 rollback <restore-YYYYMMDDTHHMMSSZ-XXXXXXXX>

O restore:
  1. valida o pacote antes da manutenção;
  2. exige confirmação digitada do DB_NAME;
  3. para somente o backend;
  4. executa restore + rollback automático em caso de falha;
  5. só religa o backend após RESTORE=OK.
EOF
}

command=${1:-}
case "$command" in
    list)
        shift
        list_backups "$@"
        ;;
    inspect)
        shift
        inspect_backup "$@"
        ;;
    restore)
        shift
        restore_backup "$@"
        ;;
    operations)
        shift
        list_operations "$@"
        ;;
    rollback)
        shift
        rollback_operation "$@"
        ;;
    ""|help|-h|--help)
        usage
        ;;
    *)
        printf 'ERRO: comando desconhecido: %s\n\n' "$command" >&2
        usage >&2
        exit 2
        ;;
esac