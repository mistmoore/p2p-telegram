# P2P Telegram — MVP local

Quarkus + Jackett (Torznab) + qBittorrent + Telegram. Solo descargas autorizadas.

## Requisitos
- JDK 25 y Maven 3.9+
- Docker Engine y Docker Compose v2+
- Bot creado con @BotFather y tu Telegram user ID
- Acceso local a los puertos 9117 (Jackett), 8081 (qBittorrent) y 8080 (Quarkus)

## Primera instalación
1. Ejecuta `./start.sh setup`. Crea `.env` si falta, arranca Compose, recupera la API key de Jackett y genera una contraseña persistente aleatoria para qBittorrent. Los secretos se guardan en `.env` con permisos 600 y no se muestran en pantalla.
2. Configura `TELEGRAM_BOT_TOKEN` (BotFather) y `TELEGRAM_ALLOWED_USER_ID` en `.env`.
3. Abre Jackett en http://localhost:9117 y configura al menos un indexador autorizado; esta parte depende de los proveedores y no puede configurarse de forma genérica.
4. Ejecuta `./start.sh` para comprobar ambos servicios e iniciar Quarkus.
5. En un chat privado con el bot, prueba `/buscar ubuntu`, `/descargar 1` y `/descargas`.

**Si ya usabas qBittorrent** con una contraseña propia, introduce primero `QBIT_PASSWORD` en `.env`: el setup la verificará en vez de sobrescribirla. Si la API key de Jackett no se encuentra, el script falla sin inventar una. El setup no instala Docker ni Java. Usa `java --source 25 Setup.java`, sin Python ni dependencias adicionales.

**Importante:** no copies las credenciales de ejemplo a entornos públicos. No subas `.env` a Git.

## Comandos
- `./start.sh setup`: configura credenciales de Jackett y qBittorrent en una instalación nueva.
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

## Raspberry Pi
- Recomendado Raspberry Pi 4/5 con sistema operativo **64 bits** y Docker Engine + Compose instalados. El script `setup` utiliza Java 25 (sin Python).
- Antes de desplegar el backend en la Pi, instala JDK 25 y Maven 3.9+, o prepara una imagen de Quarkus en contenedor en una fase posterior. Esta PR automatiza **la infraestructura y las credenciales**, no el aprovisionamiento completo del sistema operativo.
- Para usar un SSD, define `DOWNLOADS_DIR=/ruta/absoluta/del/ssd` en `.env` **antes** de `./start.sh setup`.
- No expongas Jackett/qBittorrent fuera de localhost sin un mecanismo seguro de acceso remoto (por ejemplo, túnel SSH).
