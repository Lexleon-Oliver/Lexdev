#!/bin/sh
# Testes sem Docker real: valida os argumentos e a sequência de comandos do utilitário Linux.
set -eu
SELF_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
TOOLKIT_DIR=$(CDPATH= cd -- "$SELF_DIR/../.." && pwd -P)
CTL="$TOOLKIT_DIR/partner-payload/scripts/systemproctl.sh"
COMPOSE="$TOOLKIT_DIR/partner-payload/config/docker-compose.yml"
TEST_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_DIR"' EXIT HUP INT TERM
mkdir -p "$TEST_DIR/install" "$TEST_DIR/bin"
cp "$COMPOSE" "$TEST_DIR/install/docker-compose.yml"
printf '%s\n' 'SYSTEMPRO_VERSION=2026.11.11-r3' 'SECRET_KEY=DO_NOT_ECHO_THIS' 'SYSTEMPRO_HTTP_PORT=8080' > "$TEST_DIR/install/.env"
cat > "$TEST_DIR/bin/docker" <<'DOCKER'
#!/bin/sh
printf '%s\n' "$*" >> "$DOCKER_CALLS"
case " $* " in
  *' config --quiet '*) [ "${DOCKER_CONFIG_FAIL:-0}" != 1 ] || exit 42 ;;
  *' up '*) [ "${DOCKER_UP_FAIL:-0}" != 1 ] || exit 41 ;;
esac
DOCKER
chmod +x "$TEST_DIR/bin/docker"
export SYSTEMPRO_HOME="$TEST_DIR/install" DOCKER_CALLS="$TEST_DIR/calls"
PATH="$TEST_DIR/bin:$PATH"; export PATH
run_ok() { sh "$CTL" "$@" > "$TEST_DIR/out" 2>&1; }
run_fail() { if sh "$CTL" "$@" > "$TEST_DIR/out" 2>&1; then echo "ERRO: aceitou parâmetro inválido: $*" >&2; exit 1; fi; }
assert_no_up() { if grep -q ' up ' "$DOCKER_CALLS" 2>/dev/null; then echo 'ERRO: up indevido' >&2; exit 1; fi; }
assert_no_secrets() { if grep -q 'DO_NOT_ECHO_THIS' "$TEST_DIR/out"; then echo 'ERRO: segredo exposto' >&2; exit 1; fi; }
assert_count() { actual=$(wc -l < "$DOCKER_CALLS"); [ "$actual" -eq "$1" ] || { echo "ERRO: chamadas $actual (esperado $1)" >&2; exit 1; }; }

: > "$DOCKER_CALLS"
run_ok config-check
assert_count 1
[ "$(cat "$TEST_DIR/out")" = 'SYSTEMPRO_CONFIG=OK' ]
grep -q -- 'config --quiet$' "$DOCKER_CALLS"
assert_no_secrets

: > "$DOCKER_CALLS"
run_fail config-apply backend
assert_no_up; assert_count 0
run_fail config-apply db --confirm
assert_no_up; assert_count 0
run_fail config-apply proxy --invalid
assert_no_up; assert_count 0

: > "$DOCKER_CALLS"
DOCKER_CONFIG_FAIL=1; export DOCKER_CONFIG_FAIL
run_fail config-apply backend --confirm
assert_no_up; assert_count 1
unset DOCKER_CONFIG_FAIL

: > "$DOCKER_CALLS"
run_ok config-apply backend --confirm
assert_count 2
grep -q -- 'config --quiet$' "$DOCKER_CALLS"
grep -q -- 'up -d --no-deps --no-build --pull never backend$' "$DOCKER_CALLS"
assert_no_secrets

: > "$DOCKER_CALLS"
run_ok config-apply proxy --confirm
assert_count 2
grep -q -- 'up -d --no-deps --no-build --pull never proxy$' "$DOCKER_CALLS"
assert_no_secrets

# Não altera o .env e não executa down, prune, pull nem rebuild.
[ "$(grep -c '^SECRET_KEY=DO_NOT_ECHO_THIS$' "$TEST_DIR/install/.env")" -eq 1 ]
if grep -E '(^| )(down|prune|build|pull)($| )' "$DOCKER_CALLS"; then
  echo 'ERRO: comando destrutivo/desnecessário' >&2; exit 1
fi
# Falha do compose up propaga erro.
DOCKER_UP_FAIL=1; export DOCKER_UP_FAIL
run_fail config-apply backend --confirm
unset DOCKER_UP_FAIL

echo 'OK: 12 verificações de comandos e segurança operacional Linux'
