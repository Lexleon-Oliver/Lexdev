#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:-$(date +%Y.%m.%d)-r2}
PROJECT_ROOT=${PROJECT_ROOT:-$(pwd)}
TOOLKIT_DIR=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
WORK_DIR=${WORK_DIR:-"$PROJECT_ROOT/.systempro-release/$VERSION"}
DIST_DIR=${DIST_DIR:-"$PROJECT_ROOT/dist-release"}
ROOT_PUBLIC_FILE=${LEXDEV_ROOT_PUBLIC_FILE:-"$HOME/.lexdev/root/root-public.json"}

need() { command -v "$1" >/dev/null 2>&1 || { echo "ERRO: comando obrigatório não encontrado: $1" >&2; exit 2; }; }
need docker
need sha256sum

docker info >/dev/null 2>&1 || { echo "ERRO: Docker não está em execução." >&2; exit 2; }
docker compose version >/dev/null 2>&1 || { echo "ERRO: Docker Compose v2 não está disponível." >&2; exit 2; }

for d in backend frontend proxy; do [ -d "$PROJECT_ROOT/$d" ] || { echo "ERRO: diretório ausente: $PROJECT_ROOT/$d" >&2; exit 2; }; done
[ -f "$ROOT_PUBLIC_FILE" ] || { echo "ERRO: Root Public Key ausente: $ROOT_PUBLIC_FILE" >&2; echo "Defina LEXDEV_ROOT_PUBLIC_FILE se estiver em outro local." >&2; exit 2; }
[ -f "$PROJECT_ROOT/backend/src/main/java/net/ddns/lexdev/systempro_api/licensing/LicenseStateService.java" ] || {
  echo "ERRO: backend ainda não contém o patch de licenciamento r2." >&2
  exit 2
}

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/partner/images" "$WORK_DIR/owner/images" "$DIST_DIR"
cp -a "$TOOLKIT_DIR/partner-payload/." "$WORK_DIR/partner/"
cp -a "$TOOLKIT_DIR/partner-payload/." "$WORK_DIR/owner/"
cp -a "$TOOLKIT_DIR/maintenance-payload/." "$WORK_DIR/owner/"
printf '%s\n' "$VERSION" > "$WORK_DIR/partner/VERSION"
printf '%s\n' "$VERSION" > "$WORK_DIR/owner/VERSION"

if [ -f "$TOOLKIT_DIR/docs/Manual_Instalacao_SystemPro_Parceiro.pdf" ]; then
  mkdir -p "$WORK_DIR/partner/docs" "$WORK_DIR/owner/docs"
  cp "$TOOLKIT_DIR/docs/Manual_Instalacao_SystemPro_Parceiro.pdf" "$WORK_DIR/partner/docs/"
  cp "$TOOLKIT_DIR/docs/Manual_Instalacao_SystemPro_Parceiro.pdf" "$WORK_DIR/owner/docs/"
fi
if [ -f "$TOOLKIT_DIR/docs/Guia_Proprietario_Geracao_ISOs_e_Recuperacao.pdf" ]; then
  mkdir -p "$WORK_DIR/owner/docs"
  cp "$TOOLKIT_DIR/docs/Guia_Proprietario_Geracao_ISOs_e_Recuperacao.pdf" "$WORK_DIR/owner/docs/"
fi

BACKEND_IMAGE="systempro/backend:$VERSION"
FRONTEND_IMAGE="systempro/frontend:$VERSION"
PROXY_IMAGE="systempro/proxy:$VERSION"
RECOVERY_IMAGE="systempro/recovery:$VERSION"
RESTORE_IMAGE="systempro/restore:$VERSION"
POSTGRES_IMAGE="systempro/postgres:16-alpine"
CLAMAV_IMAGE="systempro/clamav:stable"

# Root private key NEVER enters this process. Only the public descriptor is
# injected into the backend runtime before the owner-side Docker build.
ROOT_TARGET_DIR="$PROJECT_ROOT/backend/src/main/resources/licensing"
ROOT_TARGET="$ROOT_TARGET_DIR/root-public.json"
ROOT_BACKUP=""
ROOT_EXISTED=0
restore_root_resource() {
  if [ "$ROOT_EXISTED" -eq 1 ] && [ -n "$ROOT_BACKUP" ] && [ -f "$ROOT_BACKUP" ]; then
    cp "$ROOT_BACKUP" "$ROOT_TARGET"
    rm -f "$ROOT_BACKUP"
  else
    rm -f "$ROOT_TARGET"
    rmdir "$ROOT_TARGET_DIR" 2>/dev/null || true
  fi
}
trap restore_root_resource EXIT
mkdir -p "$ROOT_TARGET_DIR"
if [ -f "$ROOT_TARGET" ]; then
  ROOT_EXISTED=1
  ROOT_BACKUP="$WORK_DIR/root-public.original.json"
  cp "$ROOT_TARGET" "$ROOT_BACKUP"
fi
cp "$ROOT_PUBLIC_FILE" "$ROOT_TARGET"
chmod 0644 "$ROOT_TARGET"

# Build Host Agent owner-side. Source remains only in owner/host-agent and is
# never copied to either ISO.
echo "== Build Host Agent Linux/Windows =="
AGENT_BUILD="$WORK_DIR/agent-build"
mkdir -p "$AGENT_BUILD"
if command -v go >/dev/null 2>&1; then
  (cd "$TOOLKIT_DIR/owner/host-agent" && \
    CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags='-s -w' -o "$AGENT_BUILD/systempro-license-agent" ./cmd/systempro-license-agent && \
    CGO_ENABLED=0 GOOS=windows GOARCH=amd64 go build -trimpath -ldflags='-s -w' -o "$AGENT_BUILD/systempro-license-agent.exe" ./cmd/systempro-license-agent)
else
  docker pull golang:1.23-alpine
  docker run --rm \
    -v "$TOOLKIT_DIR/owner/host-agent:/src:ro" \
    -v "$AGENT_BUILD:/out" \
    -w /src golang:1.23-alpine sh -ec \
    "CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags='-s -w' -o /out/systempro-license-agent ./cmd/systempro-license-agent; CGO_ENABLED=0 GOOS=windows GOARCH=amd64 go build -trimpath -ldflags='-s -w' -o /out/systempro-license-agent.exe ./cmd/systempro-license-agent"
fi

for media in partner owner; do
  mkdir -p "$WORK_DIR/$media/agent/linux" "$WORK_DIR/$media/agent/windows" "$WORK_DIR/$media/agent/trust"
  cp "$AGENT_BUILD/systempro-license-agent" "$WORK_DIR/$media/agent/linux/"
  cp "$AGENT_BUILD/systempro-license-agent.exe" "$WORK_DIR/$media/agent/windows/"
  cp "$ROOT_PUBLIC_FILE" "$WORK_DIR/$media/agent/trust/root-public.json"
  chmod 0755 "$WORK_DIR/$media/agent/linux/systempro-license-agent"
done

echo "== Build backend com Root Public Key incorporada =="
docker build -t "$BACKEND_IMAGE" "$PROJECT_ROOT/backend"
restore_root_resource
trap - EXIT

echo "== Build frontend =="
docker build -t "$FRONTEND_IMAGE" "$PROJECT_ROOT/frontend"

echo "== Build proxy genérico =="
PROXY_CTX="$WORK_DIR/proxy-build"
mkdir -p "$PROXY_CTX"
cp "$TOOLKIT_DIR/partner-payload/config/nginx.conf" "$PROXY_CTX/nginx.conf"
cat > "$PROXY_CTX/Dockerfile" <<'DOCKERFILE'
FROM nginx:alpine
COPY nginx.conf /etc/nginx/nginx.conf
DOCKERFILE
docker build -t "$PROXY_IMAGE" "$PROXY_CTX"

echo "== Build ferramentas de manutenção =="
docker build -t "$RECOVERY_IMAGE" "$PROJECT_ROOT/backend/recovery"
docker build -t "$RESTORE_IMAGE" "$PROJECT_ROOT/backend/restore"

echo "== Congelando dependências externas =="
docker pull postgres:16-alpine
docker tag postgres:16-alpine "$POSTGRES_IMAGE"
docker pull clamav/clamav:stable
docker tag clamav/clamav:stable "$CLAMAV_IMAGE"

echo "== Auditoria rápida das imagens comerciais =="
docker run --rm --entrypoint sh "$BACKEND_IMAGE" -c 'if find /app -type f \( -name "*.java" -o -name "pom.xml" -o -name "*.kt" \) | grep -q .; then echo "ERRO: fonte encontrado no backend runtime" >&2; exit 40; fi'
docker run --rm --entrypoint sh "$FRONTEND_IMAGE" -c 'if find /usr/share/nginx/html -type f \( -name "*.ts" -o -name "*.map" \) | grep -q .; then echo "ERRO: TypeScript/source map encontrado no frontend runtime" >&2; exit 41; fi'

echo "== Exportando imagens comerciais =="
docker save -o "$WORK_DIR/partner/images/systempro-runtime-images.tar" \
  "$BACKEND_IMAGE" "$FRONTEND_IMAGE" "$PROXY_IMAGE" "$POSTGRES_IMAGE" "$CLAMAV_IMAGE"
cp "$WORK_DIR/partner/images/systempro-runtime-images.tar" "$WORK_DIR/owner/images/"

echo "== Exportando imagens de manutenção =="
docker save -o "$WORK_DIR/owner/images/systempro-maintenance-images.tar" "$RECOVERY_IMAGE" "$RESTORE_IMAGE"

for media in partner owner; do
  echo "== Validando ausência de fonte em $media =="
  if find "$WORK_DIR/$media" -type f \( \
      -name '*.java' -o -name '*.ts' -o -name '*.tsx' -o -name '*.go' \
      -o -name 'pom.xml' -o -name 'package.json' -o -name 'go.mod' -o -name 'go.sum' \
      -o -name '*.py' -o -name '*.map' \
    \) | grep -q .; then
    echo "ERRO: arquivo-fonte detectado no staging $media." >&2
    exit 50
  fi
  if find "$WORK_DIR/$media" -type f -name '*.sprecovery' | grep -q .; then
    echo "ERRO: .sprecovery detectado no staging $media." >&2
    exit 51
  fi
  (cd "$WORK_DIR/$media" && find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS)
done

make_iso() {
  src=$1; out=$2; label=$3
  if command -v xorriso >/dev/null 2>&1; then
    xorriso -as mkisofs -iso-level 3 -R -J -V "$label" -o "$out" "$src" >/dev/null
  elif command -v genisoimage >/dev/null 2>&1; then
    genisoimage -iso-level 3 -R -J -V "$label" -o "$out" "$src" >/dev/null
  elif command -v mkisofs >/dev/null 2>&1; then
    mkisofs -iso-level 3 -R -J -V "$label" -o "$out" "$src" >/dev/null
  else
    echo "ERRO: instale xorriso (recomendado) para gerar ISO." >&2
    exit 60
  fi
}

PARTNER_ISO="$DIST_DIR/SystemPro-Install-$VERSION.iso"
OWNER_ISO="$DIST_DIR/SystemPro-Owner-Maintenance-$VERSION.iso"
make_iso "$WORK_DIR/partner" "$PARTNER_ISO" "SYSTEMPRO_INSTALL"
make_iso "$WORK_DIR/owner" "$OWNER_ISO" "SYSTEMPRO_OWNER"

sha256sum "$PARTNER_ISO" "$OWNER_ISO" > "$DIST_DIR/SHA256SUMS-$VERSION.txt"

echo
echo "RELEASE=OK"
echo "Parceiro: $PARTNER_ISO"
echo "Proprietário: $OWNER_ISO"
echo "Checksums: $DIST_DIR/SHA256SUMS-$VERSION.txt"
echo "LICENSING=HOST_AGENT+SIGNED_LEASE"
echo "NÃO inclua .sprecovery na ISO comercial. Guarde o Recovery Kit separadamente."
