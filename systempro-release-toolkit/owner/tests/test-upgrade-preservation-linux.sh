#!/bin/sh
# Simula instalação e atualização SEM acesso ao Docker/systemd reais.
set -eu
SELF_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
TOOLKIT_DIR=$(CDPATH= cd -- "$SELF_DIR/../.." && pwd -P)
SOURCE="$TOOLKIT_DIR/partner-payload/scripts/install-linux.sh"
COMPOSE="$TOOLKIT_DIR/partner-payload/config/docker-compose.yml"
[ -f "$SOURCE" ] && [ -f "$COMPOSE" ] || { echo 'ERRO: toolkit incompleto.' >&2; exit 1; }

ROOT=$(mktemp -d)
trap 'rm -rf "$ROOT"' EXIT HUP INT TERM
mkdir -p "$ROOT/media/scripts" "$ROOT/media/agent/linux" "$ROOT/media/agent/trust" "$ROOT/media/images" "$ROOT/media/config" "$ROOT/fake-bin" "$ROOT/systemd"
cp "$COMPOSE" "$ROOT/media/config/docker-compose.yml"
cp "$TOOLKIT_DIR/partner-payload/scripts/systemproctl.sh" "$ROOT/media/scripts/systemproctl.sh"
printf '%s\n' 'release-simulado-r1' > "$ROOT/media/VERSION"
printf 'imagem de teste, não Docker' > "$ROOT/media/images/systempro-runtime-images.tar"
printf '%s\n' '{"root":"somente-teste"}' > "$ROOT/media/agent/trust/root-public.json"

# O instalador real grava a unit em /etc; a CÓPIA DE TESTE aponta EXCLUSIVAMENTE para $ROOT.
[ "$(grep -F -c 'cat > /etc/systemd/system/systempro-license-agent.service' "$SOURCE")" -eq 1 ] || {
  echo 'ERRO: ponto de redirecionamento systemd não encontrado; teste abortado.' >&2; exit 1;
}
sed "s@/etc/systemd/system/systempro-license-agent.service@$ROOT/systemd/systempro-license-agent.service@" "$SOURCE" > "$ROOT/media/scripts/install-linux.sh"
if grep -q '/etc/systemd/system/systempro-license-agent.service' "$ROOT/media/scripts/install-linux.sh"; then
  echo 'ERRO: script de teste ainda toca /etc.' >&2; exit 1
fi

cat > "$ROOT/media/agent/linux/systempro-license-agent" <<'AGENT'
#!/bin/sh
if [ "${1:-}" = 'activate' ]; then
  printf '%s\n' 'activate' >> "$TEST_LOG"
  mkdir -p "$SYSTEMPRO_HOME/instance/public"
  printf '%s\n' '{"test":"activation-preserved"}' > "$SYSTEMPRO_HOME/instance/public/instance.json"
  exit 0
fi
echo 'ERRO: agente fake recebeu ação inesperada' >&2; exit 4
AGENT
chmod +x "$ROOT/media/agent/linux/systempro-license-agent"
cat > "$ROOT/fake-bin/docker" <<'DOCKER'
#!/bin/sh
printf 'docker %s\n' "$*" >> "$TEST_LOG"
exit 0
DOCKER
cat > "$ROOT/fake-bin/systemctl" <<'CTL'
#!/bin/sh
printf 'systemctl %s\n' "$*" >> "$TEST_LOG"
exit 0
CTL
cat > "$ROOT/fake-bin/id" <<'ID'
#!/bin/sh
[ "${1:-}" = '-u' ] || exit 2
printf '0\n'
ID
chmod +x "$ROOT/fake-bin/docker" "$ROOT/fake-bin/systemctl" "$ROOT/fake-bin/id"
export SYSTEMPRO_HOME="$ROOT/install" SYSTEMPRO_ACTIVATION_CODE=FIXTURE_TEST_ONLY
export TEST_LOG="$ROOT/commands"
PATH="$ROOT/fake-bin:$PATH"; export PATH
run_install() { sh "$ROOT/media/scripts/install-linux.sh" > "$ROOT/output" 2>&1 || { cat "$ROOT/output" >&2; exit 1; }; }

run_install
[ -f "$ROOT/install/.env" ]
[ -f "$ROOT/install/instance/public/instance.json" ]
[ -f "$ROOT/systemd/systempro-license-agent.service" ]
[ "$(stat -c '%a' "$ROOT/install/.env")" = 600 ]
[ "$(stat -c '%a' "$ROOT/install/agent/config.json")" = 600 ]
[ "$(stat -c '%a' "$ROOT/install/instance/private")" = 700 ]
[ "$(grep -c '^activate$' "$ROOT/commands")" -eq 1 ]
# Dados típicos da instalação que NÃO podem ser tocados pela atualização.
printf 'dados persistidos no PostgreSQL (marcador de teste)\n' > "$ROOT/install/instance/private/IDENTITY_KEY_FIXTURE"
printf 'arquivo do cliente\n' > "$ROOT/install/instance/public/CLIENT_FIXTURE"
SECRET_OLD=$(sed -n 's/^SECRET_KEY=//p' "$ROOT/install/.env")
DB_OLD=$(sed -n 's/^DB_PASSWORD=//p' "$ROOT/install/.env")
FISCAL_OLD=$(sed -n 's/^FISCAL_ENCRYPTION_KEY=//p' "$ROOT/install/.env")
printf 'CUSTOMER_SETTING=valor-personalizado\n' >> "$ROOT/install/.env"
# Repete o instalador de uma versão nova, mantendo a mesma instalação.
printf '%s\n' 'release-simulado-r2' > "$ROOT/media/VERSION"
run_install

[ "$(grep -c '^SYSTEMPRO_VERSION=release-simulado-r2$' "$ROOT/install/.env")" -eq 1 ]
[ "$(grep -c '^CUSTOMER_SETTING=valor-personalizado$' "$ROOT/install/.env")" -eq 1 ]
[ "$(sed -n 's/^SECRET_KEY=//p' "$ROOT/install/.env")" = "$SECRET_OLD" ]
[ "$(sed -n 's/^DB_PASSWORD=//p' "$ROOT/install/.env")" = "$DB_OLD" ]
[ "$(sed -n 's/^FISCAL_ENCRYPTION_KEY=//p' "$ROOT/install/.env")" = "$FISCAL_OLD" ]
[ "$(grep -c '^activate$' "$ROOT/commands")" -eq 1 ]
[ -f "$ROOT/install/instance/private/IDENTITY_KEY_FIXTURE" ]
[ -f "$ROOT/install/instance/public/CLIENT_FIXTURE" ]
[ "$(stat -c '%a' "$ROOT/install/.env")" = 600 ]
[ "$(stat -c '%a' "$ROOT/install/agent/config.json")" = 600 ]
[ "$(grep -c 'docker compose .* up -d$' "$ROOT/commands")" -eq 2 ]
if grep -E 'docker .* (down|prune|volume rm)|DO_NOT_ECHO_THIS|FIXTURE_TEST_ONLY' "$ROOT/commands" "$ROOT/output"; then
  echo 'ERRO: destruição de dados ou exposição de segredo.' >&2; exit 1
fi
# O compose usa volumes nomeados estáveis, incluindo a base de dados.
grep -q 'name: systempro_pgdata' "$COMPOSE"
grep -q 'name: systempro_product_images' "$COMPOSE"
grep -q 'name: systempro_supplier_documents' "$COMPOSE"
grep -q 'name: systempro_backups' "$COMPOSE"
echo 'OK: upgrade Linux simulado preserva .env, identidade, caminhos e volumes; sem Docker real.'
