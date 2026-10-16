#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)

cd "$PROJECT_ROOT"

compose() {
    docker compose --profile maintenance "$@"
}

build_recovery() {
    compose build recovery
}

ensure_default_output_dir() {
    output_dir=$1
    mkdir -p "$output_dir"
    chmod 700 "$output_dir"

    if [ "$output_dir" = "$PROJECT_ROOT/recovery-kits" ]; then
        if [ ! -f "$output_dir/.gitignore" ]; then
            cat > "$output_dir/.gitignore" <<'EOF'
*
!.gitignore
EOF
            chmod 600 "$output_dir/.gitignore"
        fi
    fi
}

absolute_dir() {
    directory=$1
    mkdir -p "$directory"
    (
        cd "$directory"
        pwd -P
    )
}

current_material() {
    build_recovery
    compose run --rm --no-deps recovery current
}

create_kit() {
    output_dir=${1:-"$PROJECT_ROOT/recovery-kits"}

    ensure_default_output_dir "$output_dir"
    output_dir=$(absolute_dir "$output_dir")

    name="systempro-recovery-$(date -u +%Y%m%dT%H%M%SZ).sprecovery"

    build_recovery

    compose run --rm --no-deps \
        --user "$(id -u):$(id -g)" \
        -v "$output_dir:/recovery-out" \
        recovery create "/recovery-out/$name"

    printf '\nRECOVERY_KIT_HOST_FILE=%s/%s\n' "$output_dir" "$name"
    printf '%s\n' \
        "Copie esse .sprecovery para mídia/local externo ao servidor após validar."
}

resolve_input() {
    file=$1

    if [ ! -f "$file" ]; then
        printf 'ERRO: arquivo não encontrado: %s\n' "$file" >&2
        exit 2
    fi

    input_dir=$(CDPATH= cd -- "$(dirname -- "$file")" && pwd -P)
    input_name=$(basename -- "$file")

    printf '%s|%s\n' "$input_dir" "$input_name"
}

verify_kit() {
    [ "$#" -eq 1 ] || {
        printf '%s\n' "Uso: $0 verify <arquivo.sprecovery>" >&2
        exit 2
    }

    build_recovery

    resolved=$(resolve_input "$1")
    input_dir=${resolved%%|*}
    input_name=${resolved#*|}

    compose run --rm --no-deps \
        --user "$(id -u):$(id -g)" \
        -v "$input_dir:/recovery-in:ro" \
        recovery verify "/recovery-in/$input_name"
}

extract_kit() {
    [ "$#" -ge 2 ] || {
        printf '%s\n' \
            "Uso: $0 extract <arquivo.sprecovery> <diretorio-saida> [--force]" >&2
        exit 2
    }

    kit=$1
    output_dir=$2
    force=${3:-}

    case "$force" in
        ""|--force) ;;
        *)
            printf 'ERRO: opção desconhecida: %s\n' "$force" >&2
            exit 2
            ;;
    esac

    build_recovery

    resolved=$(resolve_input "$kit")
    input_dir=${resolved%%|*}
    input_name=${resolved#*|}

    mkdir -p "$output_dir"
    chmod 700 "$output_dir"
    output_dir=$(absolute_dir "$output_dir")

    if [ "$force" = "--force" ]; then
        force_arg="--force"
    else
        force_arg=""
    fi

    compose run --rm --no-deps \
        --user "$(id -u):$(id -g)" \
        -v "$input_dir:/recovery-in:ro" \
        -v "$output_dir:/recovery-out" \
        recovery extract \
        "/recovery-in/$input_name" \
        /recovery-out \
        $force_arg

    printf '\nRECOVERY_MATERIAL_HOST_DIR=%s\n' "$output_dir"
    printf '%s\n' \
        "O diretório contém segredo em claro. Mantenha 0700/0600 e remova-o quando terminar a recuperação."
}

usage() {
    cat <<EOF
SystemPro Recovery Kit v1

Uso:
  $0 current
  $0 create [diretorio-saida]
  $0 verify <arquivo.sprecovery>
  $0 extract <arquivo.sprecovery> <diretorio-saida> [--force]

Regras:
  - senha de suporte mínima: 16 caracteres;
  - senha nunca é argumento, variável de ambiente ou arquivo;
  - .sprecovery usa Argon2id + AES-256-GCM;
  - FISCAL_ENCRYPTION_KEY é obrigatória para criação;
  - /data/systempro-instance é incluído automaticamente quando tiver arquivos;
  - verify nunca imprime segredos;
  - extract grava segredos somente no diretório informado com permissão 0600.
EOF
}

command=${1:-}

case "$command" in
    current)
        shift
        current_material "$@"
        ;;
    create)
        shift
        create_kit "$@"
        ;;
    verify)
        shift
        verify_kit "$@"
        ;;
    extract)
        shift
        extract_kit "$@"
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
