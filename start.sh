#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

log() { printf '[start] %s\n' "$*"; }
die() { printf '[start] ERROR: %s\n' "$*" >&2; exit 1; }

for command in docker mvn java curl; do
  command -v "$command" >/dev/null 2>&1 || die "Falta '$command' en PATH."
done

[[ -f .env ]] || die "Falta .env. Copia .env.example y completa la configuración."
[[ -r .env ]] || die "No se puede leer .env."

# The .env file is trusted shell input; keep it private and never commit it.
set -a
# shellcheck disable=SC1091
source .env
set +a

for name in TELEGRAM_BOT_TOKEN TELEGRAM_ALLOWED_USER_ID JACKETT_API_KEY QBIT_PASSWORD; do
  [[ -n "${!name:-}" ]] || die "Falta configurar $name en .env."
done
[[ "$TELEGRAM_ALLOWED_USER_ID" =~ ^[0-9]+$ ]] || die "TELEGRAM_ALLOWED_USER_ID debe ser numérico."

: "${JACKETT_URL:=http://localhost:9117}"
: "${QBIT_URL:=http://localhost:8081}"
: "${STARTUP_TIMEOUT_SECONDS:=60}"
[[ "$STARTUP_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] || die "STARTUP_TIMEOUT_SECONDS debe ser un entero positivo."

docker info >/dev/null 2>&1 || die "Docker no responde. Comprueba docker.service y los permisos de acceso al socket."

for name in jackett qbittorrent; do
  docker container inspect "$name" >/dev/null 2>&1 || die "No existe el contenedor '$name'. Créalo y configúralo antes de ejecutar start.sh."
  if [[ "$(docker inspect -f '{{.State.Running}}' "$name")" != "true" ]]; then
    log "Arrancando contenedor $name"
    docker start "$name" >/dev/null || die "No se pudo arrancar $name."
  else
    log "$name ya está arrancado"
  fi
done

wait_for_http() {
  local name="$1" url="$2" deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
  local code
  log "Esperando a $name en $url (máximo ${STARTUP_TIMEOUT_SECONDS}s)"
  while (( SECONDS < deadline )); do
    # Any HTTP response confirms the web server is accepting connections.
    # Authentication and application-level errors are handled by Quarkus.
    code="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 3 "$url" || true)"
    if [[ "$code" =~ ^[1-5][0-9][0-9]$ ]]; then
      log "$name responde (HTTP $code)"
      return 0
    fi
    sleep 2
  done
  die "$name no responde en $url. Revisa 'docker logs $name'."
}

wait_for_http jackett "${JACKETT_URL%/}/UI/Dashboard"
wait_for_http qbittorrent "${QBIT_URL%/}/api/v2/app/version"

log "Iniciando Quarkus en modo desarrollo"
exec mvn quarkus:dev
