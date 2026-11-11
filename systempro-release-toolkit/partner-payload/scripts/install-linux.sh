#!/bin/sh
set -eu

MEDIA_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
INSTALL_DIR=${SYSTEMPRO_HOME:-/opt/systempro}
IMAGE_ARCHIVE="$MEDIA_DIR/images/systempro-runtime-images.tar"
COMPOSE_SOURCE="$MEDIA_DIR/config/docker-compose.yml"
CTL_SOURCE="$MEDIA_DIR/scripts/systemproctl.sh"
AGENT_SOURCE="$MEDIA_DIR/agent/linux/systempro-license-agent"
ROOT_PUBLIC_SOURCE="$MEDIA_DIR/agent/trust/root-public.json"
VERSION=$(tr -d '\r\n' < "$MEDIA_DIR/VERSION")

fail() { echo "ERRO: $*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || fail "comando obrigatório não encontrado: $1"; }
rand_b64() { head -c "$1" /dev/urandom | base64 | tr -d '\n'; }
env_has() { grep -q "^$1=" "$ENV_FILE" 2>/dev/null; }
env_add() { env_has "$1" || printf '%s=%s\n' "$1" "$2" >> "$ENV_FILE"; }
env_get() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1 | sed 's/^"//;s/"$//'; }
json_escape() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }

need docker
need base64
need sed
need sha256sum
need systemctl

docker info >/dev/null 2>&1 || fail "Docker não está em execução ou o usuário não tem permissão para acessá-lo."
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 não está disponível."
[ -f "$IMAGE_ARCHIVE" ] || fail "arquivo de imagens não encontrado: $IMAGE_ARCHIVE"
[ -f "$COMPOSE_SOURCE" ] || fail "docker-compose.yml não encontrado na mídia."
[ -x "$AGENT_SOURCE" ] || fail "Host Agent Linux não encontrado na mídia."
[ -f "$ROOT_PUBLIC_SOURCE" ] || fail "Root Public Key de licenciamento não encontrada na mídia."

if [ "$(id -u)" -ne 0 ]; then
  fail "execute o instalador como root/sudo."
fi

if [ -f "$MEDIA_DIR/SHA256SUMS" ]; then
  (cd "$MEDIA_DIR" && sha256sum -c SHA256SUMS)
fi

mkdir -p "$INSTALL_DIR"
cp "$COMPOSE_SOURCE" "$INSTALL_DIR/docker-compose.yml"
cp "$CTL_SOURCE" "$INSTALL_DIR/systemproctl"
chmod 0755 "$INSTALL_DIR/systemproctl"

ENV_FILE="$INSTALL_DIR/.env"
FIRST_INSTALL=0
if [ ! -f "$ENV_FILE" ]; then
  FIRST_INSTALL=1
  HTTP_PORT=${SYSTEMPRO_HTTP_PORT:-8080}
  case "$HTTP_PORT" in ''|*[!0-9]*) fail "SYSTEMPRO_HTTP_PORT deve ser numérica." ;; esac
  [ "$HTTP_PORT" -ge 1 ] && [ "$HTTP_PORT" -le 65535 ] || fail "porta HTTP inválida: $HTTP_PORT"
  INSTANCE_DIR=${SYSTEMPRO_INSTANCE_DIR:-"$INSTALL_DIR/instance"}

  umask 077
  cat > "$ENV_FILE" <<ENVEOF
# SystemPro - configuração persistente da instalação
SYSTEMPRO_VERSION=$VERSION
SYSTEMPRO_HTTP_PORT=$HTTP_PORT
SYSTEMPRO_INSTANCE_DIR=$INSTANCE_DIR

# Banco
DB_HOST=db
DB_PORT=5432
DB_NAME=systempro
DB_USER=systempro_app
DB_PASSWORD=$(rand_b64 36)

# JWT
SECRET_KEY=$(rand_b64 64)
SECRET_REFRESH_KEY=$(rand_b64 64)
JWT_ISSUER=systempro
JWT_AUDIENCE=systempro-web
JWT_EXPIRATION=900000
JWT_REFRESH_EXPIRATION=604800000

# Storage
STORAGE_PROVIDER=local
STORAGE_LOCAL_STORAGE_LOCATION=/data/supplier-documents
STORAGE_ENDPOINT=
STORAGE_REGION=us-east-1
STORAGE_ACCESS_KEY=
STORAGE_SECRET_KEY=
STORAGE_BUCKET=systempro-documents
STORAGE_PATH_STYLE_ACCESS=false
STORAGE_MAX_FILE_SIZE_BYTES=15728640
SYSTEMPRO_IMAGES_STORAGE_LOCATION=/data/product-images

# ClamAV
CLAMAV_HOST=clamav
CLAMAV_PORT=3310
CLAMAV_TIMEOUT_SECONDS=30

# Fiscal
FISCAL_ENABLED=true
FISCAL_PRODUCTION_READY=false
FISCAL_ENCRYPTION_KEY=$(rand_b64 48)
FISCAL_CONNECT_TIMEOUT_SECONDS=10
FISCAL_REQUEST_TIMEOUT_SECONDS=30
FISCAL_SCHEMA_VERSION=4.00
FISCAL_PRODUCT_VERSION=SystemPro-$VERSION
FISCAL_TRUST_STORE_PATH=
FISCAL_TRUST_STORE_PASSWORD=
FISCAL_SCHEMA_LOCATION=classpath:/fiscal/nfce/PL_010f/nfe_v4.00.xsd

# IA (desabilitada por padrão)
AI_ENABLED=false
AI_PROVIDER=openai
AI_MAX_OUTPUT_TOKENS=600
AI_CONNECT_TIMEOUT_SECONDS=10
AI_REQUEST_TIMEOUT_SECONDS=30
OPENAI_API_KEY=
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-6-luna
GEMINI_API_KEY=
GEMINI_BASE_URL=https://generativelanguage.googleapis.com/v1
GEMINI_MODEL=gemini-3.5-flash-lite

# Backup
BACKUP_STORAGE_LOCATION=/data/backups
BACKUP_PG_DUMP_TIMEOUT_SECONDS=600
BACKUP_SCHEDULING_ENABLED=true
BACKUP_SCHEDULING_CRON="0 0 2 * * *"
BACKUP_SCHEDULING_ZONE=America/Sao_Paulo
BACKUP_SCHEDULING_MAX_AGE_HOURS=24
BACKUP_SCHEDULING_CHECK_INTERVAL_MILLIS=3600000
BACKUP_SCHEDULING_INITIAL_DELAY_MILLIS=60000
BACKUP_RETENTION_SCHEDULED_DAYS=30

# Licenciamento - Host Agent
SYSTEMPRO_LICENSE_SERVER_URL=https://license.lexdev.net.br
SYSTEMPRO_LICENSE_ACTIVATION_PATH=/api/v1/activations
SYSTEMPRO_LICENSE_HEARTBEAT_PATH=/api/v1/heartbeat
SYSTEMPRO_LICENSE_RENEWAL_PATH=/api/v1/leases/renewals
SYSTEMPRO_LICENSE_REQUEST_TIMEOUT_SECONDS=30
SYSTEMPRO_LICENSE_HEARTBEAT_INTERVAL_SECONDS=21600
SYSTEMPRO_LICENSE_RENEWAL_INTERVAL_SECONDS=86400
SYSTEMPRO_LICENSE_JITTER_SECONDS=900
LICENSE_CACHE_MILLIS=5000
ENVEOF
  chmod 0600 "$ENV_FILE"
else
  # Upgrade idempotente r1 -> r2: preserva todos os segredos já existentes.
  if env_has SYSTEMPRO_VERSION; then
    tmp="$ENV_FILE.tmp.$$"
    sed "s/^SYSTEMPRO_VERSION=.*/SYSTEMPRO_VERSION=$VERSION/" "$ENV_FILE" > "$tmp"
    chmod 0600 "$tmp"
    mv "$tmp" "$ENV_FILE"
  else
    printf '\nSYSTEMPRO_VERSION=%s\n' "$VERSION" >> "$ENV_FILE"
  fi
  env_add SYSTEMPRO_INSTANCE_DIR "${SYSTEMPRO_INSTANCE_DIR:-$INSTALL_DIR/instance}"
  env_add DB_HOST db; env_add DB_PORT 5432
  env_add STORAGE_PROVIDER local; env_add STORAGE_LOCAL_STORAGE_LOCATION /data/supplier-documents
  env_add STORAGE_ENDPOINT ''; env_add STORAGE_REGION us-east-1; env_add STORAGE_ACCESS_KEY ''; env_add STORAGE_SECRET_KEY ''
  env_add STORAGE_BUCKET systempro-documents; env_add STORAGE_PATH_STYLE_ACCESS false; env_add STORAGE_MAX_FILE_SIZE_BYTES 15728640
  env_add SYSTEMPRO_IMAGES_STORAGE_LOCATION /data/product-images
  env_add CLAMAV_HOST clamav; env_add CLAMAV_PORT 3310; env_add CLAMAV_TIMEOUT_SECONDS 30
  env_add FISCAL_CONNECT_TIMEOUT_SECONDS 10; env_add FISCAL_REQUEST_TIMEOUT_SECONDS 30; env_add FISCAL_SCHEMA_VERSION 4.00
  env_add FISCAL_PRODUCT_VERSION "SystemPro-$VERSION"; env_add FISCAL_TRUST_STORE_PATH ''; env_add FISCAL_TRUST_STORE_PASSWORD ''
  env_add FISCAL_SCHEMA_LOCATION 'classpath:/fiscal/nfce/PL_010f/nfe_v4.00.xsd'
  env_add AI_PROVIDER openai; env_add AI_MAX_OUTPUT_TOKENS 600; env_add AI_CONNECT_TIMEOUT_SECONDS 10; env_add AI_REQUEST_TIMEOUT_SECONDS 30
  env_add OPENAI_API_KEY ''; env_add OPENAI_BASE_URL https://api.openai.com/v1; env_add OPENAI_MODEL gpt-6-luna
  env_add GEMINI_API_KEY ''; env_add GEMINI_BASE_URL https://generativelanguage.googleapis.com/v1; env_add GEMINI_MODEL gemini-3.5-flash-lite
  env_add BACKUP_PG_DUMP_TIMEOUT_SECONDS 600; env_add BACKUP_SCHEDULING_ENABLED true
  env_add BACKUP_SCHEDULING_CRON '"0 0 2 * * *"'; env_add BACKUP_SCHEDULING_ZONE America/Sao_Paulo
  env_add BACKUP_SCHEDULING_MAX_AGE_HOURS 24; env_add BACKUP_SCHEDULING_CHECK_INTERVAL_MILLIS 3600000
  env_add BACKUP_SCHEDULING_INITIAL_DELAY_MILLIS 60000; env_add BACKUP_RETENTION_SCHEDULED_DAYS 30
  env_add SYSTEMPRO_LICENSE_SERVER_URL https://license.lexdev.net.br
  env_add SYSTEMPRO_LICENSE_ACTIVATION_PATH /api/v1/activations
  env_add SYSTEMPRO_LICENSE_HEARTBEAT_PATH /api/v1/heartbeat
  env_add SYSTEMPRO_LICENSE_RENEWAL_PATH /api/v1/leases/renewals
  env_add SYSTEMPRO_LICENSE_REQUEST_TIMEOUT_SECONDS 30
  env_add SYSTEMPRO_LICENSE_HEARTBEAT_INTERVAL_SECONDS 21600
  env_add SYSTEMPRO_LICENSE_RENEWAL_INTERVAL_SECONDS 86400
  env_add SYSTEMPRO_LICENSE_JITTER_SECONDS 900
  env_add LICENSE_CACHE_MILLIS 5000
  chmod 0600 "$ENV_FILE"
fi

INSTANCE_DIR=$(env_get SYSTEMPRO_INSTANCE_DIR)
[ -n "$INSTANCE_DIR" ] || fail "SYSTEMPRO_INSTANCE_DIR não definido."
mkdir -p "$INSTANCE_DIR/private" "$INSTANCE_DIR/public"
chmod 0700 "$INSTANCE_DIR/private"
chmod 0755 "$INSTANCE_DIR/public"

AGENT_DIR="$INSTALL_DIR/agent"
mkdir -p "$AGENT_DIR/trust"
cp "$AGENT_SOURCE" "$AGENT_DIR/systempro-license-agent"
cp "$ROOT_PUBLIC_SOURCE" "$AGENT_DIR/trust/root-public.json"
chmod 0755 "$AGENT_DIR/systempro-license-agent"
chmod 0644 "$AGENT_DIR/trust/root-public.json"

SERVER_URL=$(env_get SYSTEMPRO_LICENSE_SERVER_URL)
ACT_PATH=$(env_get SYSTEMPRO_LICENSE_ACTIVATION_PATH)
HB_PATH=$(env_get SYSTEMPRO_LICENSE_HEARTBEAT_PATH)
RENEW_PATH=$(env_get SYSTEMPRO_LICENSE_RENEWAL_PATH)
REQ_TIMEOUT=$(env_get SYSTEMPRO_LICENSE_REQUEST_TIMEOUT_SECONDS)
HB_INTERVAL=$(env_get SYSTEMPRO_LICENSE_HEARTBEAT_INTERVAL_SECONDS)
RENEW_INTERVAL=$(env_get SYSTEMPRO_LICENSE_RENEWAL_INTERVAL_SECONDS)
JITTER=$(env_get SYSTEMPRO_LICENSE_JITTER_SECONDS)

cat > "$AGENT_DIR/config.json" <<JSONEOF
{
  "serverUrl": "$(json_escape "$SERVER_URL")",
  "activationPath": "$(json_escape "$ACT_PATH")",
  "heartbeatPath": "$(json_escape "$HB_PATH")",
  "renewalPath": "$(json_escape "$RENEW_PATH")",
  "systemProVersion": "$(json_escape "$VERSION")",
  "instanceDir": "$(json_escape "$INSTANCE_DIR")",
  "rootPublicFile": "$(json_escape "$AGENT_DIR/trust/root-public.json")",
  "requestTimeoutSeconds": $REQ_TIMEOUT,
  "heartbeatIntervalSeconds": $HB_INTERVAL,
  "renewalIntervalSeconds": $RENEW_INTERVAL,
  "jitterSeconds": $JITTER
}
JSONEOF
chmod 0600 "$AGENT_DIR/config.json"

if [ ! -f "$INSTANCE_DIR/public/instance.json" ]; then
  ACTIVATION_CODE=${SYSTEMPRO_ACTIVATION_CODE:-}
  if [ -z "$ACTIVATION_CODE" ]; then
    printf 'Código de ativação do SystemPro: ' >&2
    if [ -t 0 ]; then stty -echo; fi
    IFS= read -r ACTIVATION_CODE
    if [ -t 0 ]; then stty echo; printf '\n' >&2; fi
  fi
  [ -n "$ACTIVATION_CODE" ] || fail "código de ativação não informado."
  printf '%s\n' "$ACTIVATION_CODE" | "$AGENT_DIR/systempro-license-agent" activate --code-stdin --config "$AGENT_DIR/config.json"
  unset ACTIVATION_CODE SYSTEMPRO_ACTIVATION_CODE || true
fi

cat > /etc/systemd/system/systempro-license-agent.service <<SERVICEEOF
[Unit]
Description=SystemPro License Host Agent
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
ExecStart=$AGENT_DIR/systempro-license-agent service --config $AGENT_DIR/config.json
Restart=on-failure
RestartSec=30
NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true
ProtectSystem=strict
ReadOnlyPaths=$AGENT_DIR
ReadWritePaths=$INSTANCE_DIR

[Install]
WantedBy=multi-user.target
SERVICEEOF
systemctl daemon-reload
systemctl enable --now systempro-license-agent.service

printf '%s\n' "Carregando imagens do SystemPro..."
docker load -i "$IMAGE_ARCHIVE"

printf '%s\n' "Iniciando SystemPro..."
docker compose -f "$INSTALL_DIR/docker-compose.yml" --env-file "$ENV_FILE" up -d

printf '\nSYSTEMPRO_INSTALL=OK\n'
printf 'Versão: %s\n' "$VERSION"
PORT=$(env_get SYSTEMPRO_HTTP_PORT)
printf 'Acesso local: http://localhost:%s\n' "$PORT"
printf 'Diretório: %s\n' "$INSTALL_DIR"
printf 'Licença: %s/systemproctl license\n' "$INSTALL_DIR"
if [ "$FIRST_INSTALL" -eq 1 ]; then
  echo "IMPORTANTE: $ENV_FILE e $INSTANCE_DIR/private contêm segredos únicos. Não substitua nem copie entre clientes."
  echo "Gere o Recovery Kit .sprecovery após validar a instalação."
fi
