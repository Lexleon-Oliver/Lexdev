#!/bin/sh
set -eu
MEDIA_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
ARCHIVE="$MEDIA_DIR/images/systempro-maintenance-images.tar"
[ -f "$ARCHIVE" ] || { echo "Arquivo não encontrado: $ARCHIVE" >&2; exit 2; }
docker info >/dev/null 2>&1 || { echo "Docker indisponível." >&2; exit 2; }
docker load -i "$ARCHIVE"
echo "SYSTEMPRO_MAINTENANCE_IMAGES=OK"
