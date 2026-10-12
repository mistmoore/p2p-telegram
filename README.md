# P2P Telegram — MVP local

Quarkus + Jackett (Torznab) + qBittorrent + Telegram. Solo descargas autorizadas.

## Requisitos
- JDK 25 y Maven 3.9+
- Docker Engine y Docker Compose v2+
- Bot creado con @BotFather y tu Telegram user ID
- Acceso local a los puertos 9117 (Jackett), 8081 (qBittorrent) y 8080 (Quarkus)

## Primera instalación
1. Copia `.env.example` a `.env` y configura `TELEGRAM_BOT_TOKEN` y `TELEGRAM_ALLOWED_USER_ID`.
2. Ejecuta `./start.sh infra` para crear y arrancar los contenedores.
3. Abre Jackett en http://localhost:9117, configura al menos un indexador autorizado y copia su API key a `JACKETT_API_KEY` en `.env`.
4. Abre qBittorrent en http://localhost:8081. En instalaciones nuevas de LinuxServer, consulta la contraseña temporal de admin en `docker compose logs qbittorrent`; cámbiala en la Web UI y coloca la contraseña definitiva en `QBIT_PASSWORD` de `.env`.
5. Ejecuta `./start.sh` para comprobar que ambos servicios responden e iniciar Quarkus.
6. En un chat privado con el bot, prueba `/buscar ubuntu`, `/descargar 1` y `/descargas`.

**Importante:** no copies las credenciales de ejemplo a entornos públicos. No subas `.env` a Git.

## Comandos
- `./start.sh`: levanta infraestructura, espera a las interfaces HTTP e inicia Quarkus.
- `./start.sh infra`: crea/inicia solo Jackett y qBittorrent.
- `./start.sh stop`: detiene ambos contenedores, sin borrar datos.
- `./start.sh status`: muestra el estado de los contenedores.
- `./start.sh logs`: sigue los logs de los contenedores.
- `docker compose config`: valida la configuración Compose.
- `docker compose down`: elimina los contenedores y la red, **sin** borrar los directorios persistentes. No uses `down -v` sin revisar los datos.

## Datos, red y despliegue
- Configuración persistente: `./data/jackett` y `./data/qbittorrent`.
- Descargas: `DOWNLOADS_DIR` si se define; por defecto `./data/downloads`. Usa una ruta absoluta si quieres guardar en un SSD.
- Los servicios Web UI se publican solo en `127.0.0.1` del host. El puerto P2P de qBittorrent (TCP/UDP, 6881 por defecto) sí se publica para conectividad entre pares.
- En Linux ajusta `PUID` y `PGID` a `id -u` e `id -g` si no son 1000.
- Las imágenes LinuxServer tienen variantes para arquitecturas compatibles; comprueba la disponibilidad concreta para tu Raspberry Pi antes de migrar.
- Los healthchecks comprueban apertura de puerto TCP; **no** garantizan autenticación, funcionamiento del indexador ni descargas.
- Si ya existen contenedores antiguos con puertos 9117/8081 ocupados, detenlos y planifica la migración de su configuración antes de iniciar Compose. Los contenedores de este proyecto se llaman `p2p-jackett` y `p2p-qbittorrent`.

## Notas de seguridad y limitaciones
- El bot utiliza long polling; no requiere webhook ni IP pública.
- Las selecciones, el offset y las descargas enviadas se guardan en SQLite (`STATE_DB_URL`).
- La validación de URLs de descarga sigue siendo básica: no expongas este servicio públicamente sin revisar SSRF y restringir destinos.
- No expongas las Web UI ni sus credenciales a Internet.
- El script de arranque interpreta `.env` como código de shell; mantenlo bajo tu control.
- La integración completa Telegram → Jackett → qBittorrent requiere validación en tu equipo.
