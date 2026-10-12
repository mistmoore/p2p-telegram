#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

log() { printf '[start] %s\n' "$*"; }
die() { printf '[start] ERROR: %s\n' "$*" >&2; exit 1; }

if [[ "${1:-}" == "setup" ]]; then
  command -v docker >/dev/null 2>&1 || die "Falta Docker."
  docker compose version >/dev/null 2>&1 || die "Falta Docker Compose."
  command -v java >/dev/null 2>&1 || die "Falta Java 25."
  exec java --source 25 Setup.java
fi

for command in docker curl; do
  command -v "$command" >/dev/null 2>&1 || die "Falta '$command' en PATH."
done

[[ -f .env ]] || die "Falta .env. Copia .env.example y completa la configuración."
[[ -r .env ]] || die "No se puede leer .env."

# .env is trusted shell input; never use files from untrusted sources.
set -a
# shellcheck disable=SC1091
source .env
set +a

for name in TELEGRAM_BOT_TOKEN TELEGRAM_ALLOWED_USER_ID; do
  [[ -n "${!name:-}" ]] || die "Falta configurar $name en .env."
done
[[ "$TELEGRAM_ALLOWED_USER_ID" =~ ^[0-9]+$ ]] || die "TELEGRAM_ALLOWED_USER_ID debe ser numérico."

: "${JACKETT_URL:=http://localhost:9117}"
: "${QBIT_URL:=http://localhost:8081}"
: "${STARTUP_TIMEOUT_SECONDS:=90}"
[[ "$STARTUP_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] || die "STARTUP_TIMEOUT_SECONDS debe ser un entero positivo."

docker info >/dev/null 2>&1 || die "Docker no responde. Comprueba docker.service y el acceso al socket."
docker compose version >/dev/null 2>&1 || die "Falta Docker Compose (docker compose)."

case "${1:-start}" in
  start)
    log "Iniciando infraestructura con Docker Compose"
    docker compose up -d || die "No se pudo iniciar Compose. Comprueba 'docker compose logs'."
    ;;
  infra)
    docker compose up -d
    exit 0
    ;;
  stop)
    docker compose stop
    exit 0
    ;;
  status)
    docker compose ps
    exit 0
    ;;
  logs)
    exec docker compose logs -f --tail=100
    ;;
  *)
    die "Uso: ./start.sh [start|setup|infra|stop|status|logs]"
    ;;
esac

wait_for_http() {
  local name="$1" url="$2" deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
  local code
  log "Esperando a $name en $url (máximo ${STARTUP_TIMEOUT_SECONDS}s)"
  while (( SECONDS < deadline )); do
    code="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 3 "$url" || true)"
    if [[ "$code" =~ ^[1-5][0-9][0-9]$ ]]; then
      log "$name responde (HTTP $code)"
      return 0
    fi
    sleep 2
  done
  die "$name no responde en $url. Revisa 'docker compose ps' y 'docker compose logs $name'."
}

wait_for_http jackett "${JACKETT_URL%/}/UI/Dashboard"
wait_for_http qbittorrent "${QBIT_URL%/}/api/v2/app/version"

[[ -n "${JACKETT_API_KEY:-}" ]] || die "Configura JACKETT_API_KEY en .env después de inicializar Jackett."
[[ -n "${QBIT_PASSWORD:-}" ]] || die "Configura QBIT_PASSWORD en .env después de inicializar qBittorrent."

for command in mvn java; do
  command -v "$command" >/dev/null 2>&1 || die "Falta $command para ejecutar Quarkus."
done
log "Iniciando Quarkus en modo desarrollo"
exec mvn quarkus:dev
