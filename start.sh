#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
umask 077

log() { printf '[start] %s\n' "$*"; }
die() { printf '[start] ERROR: %s\n' "$*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || die "Falta Docker Engine."
docker info >/dev/null 2>&1 || die "Docker no responde. Comprueba permisos y docker.service."
docker compose version >/dev/null 2>&1 || die "Falta Docker Compose."

case "${1:-start}" in
  setup)
    [[ -f .env ]] || cp .env.example .env
    chmod 600 .env
    log "Arrancando Jackett y qBittorrent"
    docker compose up -d jackett qbittorrent
    log "Inicializando credenciales mediante Java 25 dentro de Docker"
    # No host JDK or Maven, no Docker socket mounted inside the bootstrap container.
    docker compose logs --no-color qbittorrent | docker run --rm -i \
      --network host --user "$(id -u):$(id -g)" \
      -v "$PWD:/workspace" -w /workspace \
      eclipse-temurin:25-jdk java --source 25 Setup.java
    ;;
  start)
    [[ -f .env ]] || die "Falta .env. Ejecuta './start.sh setup' primero."
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
    [[ -n "${TELEGRAM_BOT_TOKEN:-}" && -n "${TELEGRAM_ALLOWED_USER_ID:-}" ]] ||
      die "Configura TELEGRAM_BOT_TOKEN y TELEGRAM_ALLOWED_USER_ID en .env."
    [[ "$TELEGRAM_ALLOWED_USER_ID" =~ ^[0-9]+$ ]] || die "TELEGRAM_ALLOWED_USER_ID debe ser numérico."
    [[ -n "${JACKETT_API_KEY:-}" && -n "${QBIT_PASSWORD:-}" ]] ||
      die "Faltan credenciales internas. Ejecuta './start.sh setup'."
    docker compose up -d --build
    docker compose ps
    ;;
  infra)
    [[ -f .env ]] || cp .env.example .env
    docker compose up -d jackett qbittorrent
    ;;
  stop)
    docker compose stop
    ;;
  status)
    docker compose ps
    ;;
  logs)
    docker compose logs -f --tail=100
    ;;
  *)
    die "Uso: ./start.sh [setup|start|infra|stop|status|logs]"
    ;;
esac
