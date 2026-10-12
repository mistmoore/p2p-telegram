#!/usr/bin/env bash
set -euo pipefail

if [[ ! -f .env ]]; then
  printf 'Falta .env. Copia .env.example y completa la configuracion.\n' >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  printf 'Docker es necesario para iniciar Jackett y qBittorrent.\n' >&2
  exit 1
fi

docker start jackett qbittorrent >/dev/null

set -a
source .env
set +a

exec mvn quarkus:dev
