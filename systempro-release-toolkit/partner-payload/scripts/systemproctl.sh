#!/bin/sh
set -eu

BASE_DIR=${SYSTEMPRO_HOME:-/opt/systempro}
COMPOSE_FILE="$BASE_DIR/docker-compose.yml"
ENV_FILE="$BASE_DIR/.env"
AGENT="$BASE_DIR/agent/systempro-license-agent"
AGENT_CONFIG="$BASE_DIR/agent/config.json"

require_install() {
  [ -f "$COMPOSE_FILE" ] && [ -f "$ENV_FILE" ] || {
    echo "SystemPro não está instalado em $BASE_DIR." >&2
    exit 2
  }
}

compose() { docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"; }

require_install
case "${1:-status}" in
  start) compose up -d; command -v systemctl >/dev/null 2>&1 && systemctl start systempro-license-agent.service 2>/dev/null || true ;;
  stop) compose stop ;;
  restart) compose restart; command -v systemctl >/dev/null 2>&1 && systemctl restart systempro-license-agent.service 2>/dev/null || true ;;
  status) compose ps ;;
  logs) shift; compose logs --tail=200 "$@" ;;
  version) grep '^SYSTEMPRO_VERSION=' "$ENV_FILE" | cut -d= -f2- ;;
  # Não imprime o docker compose config completo: ele contém segredos interpolados.
  config-check)
    compose config --quiet
    echo "SYSTEMPRO_CONFIG=OK"
    ;;
  config-apply)
    target=${2:-}
    confirm=${3:-}
    case "$target" in
      backend|proxy) ;;
      *) echo "Uso: systemproctl config-apply {backend|proxy} --confirm" >&2; exit 2 ;;
    esac
    [ "$confirm" = "--confirm" ] || {
      echo "Confirme explicitamente: systemproctl config-apply $target --confirm" >&2
      exit 2
    }
    # Valida todas as variáveis antes de recriar qualquer container.
    compose config --quiet
    # Sem pull, rebuild, dependências nem remoção de volumes.
    compose up -d --no-deps --no-build --pull never "$target"
    echo "SYSTEMPRO_CONFIG_APPLIED=$target"
    echo "ATENÇÃO: mudanças em licenciamento/Host Agent não são aplicadas por este comando."
    ;;
  license)
    [ -x "$AGENT" ] || { echo "Host Agent não encontrado." >&2; exit 2; }
    "$AGENT" status --config "$AGENT_CONFIG"
    ;;
  license-once)
    [ -x "$AGENT" ] || { echo "Host Agent não encontrado." >&2; exit 2; }
    "$AGENT" once --config "$AGENT_CONFIG"
    ;;
  license-logs)
    command -v journalctl >/dev/null 2>&1 || { echo "journalctl não disponível." >&2; exit 2; }
    journalctl -u systempro-license-agent.service -n 200 --no-pager
    ;;
  *)
    cat <<USAGE
Uso: systemproctl {start|stop|restart|status|logs [serviço]|version|config-check|config-apply {backend|proxy} --confirm|license|license-once|license-logs}

Este utilitário nunca remove volumes ou dados.
USAGE
    exit 2
    ;;
esac
