# P2P Telegram — Raspberry Pi / Linux

Bot de Telegram con Quarkus, Jackett y qBittorrent. Solo para descargas autorizadas.

## Requisitos en Raspberry Pi
- Raspberry Pi OS **64 bits** en una Raspberry Pi 4/5 (ARM64).
- Docker Engine y el plugin Docker Compose funcionando con el usuario actual.
- Git para clonar el repositorio. **No hace falta instalar Java, Maven ni Python en el host.**
- Bot creado en @BotFather y tu identificador numérico de Telegram.
- Un indexador autorizado que configurar en Jackett.

## Primera instalación

```bash
git clone https://github.com/mistmoore/p2p-telegram.git
cd p2p-telegram
./start.sh setup
```

`setup` crea `.env` si falta, arranca Jackett y qBittorrent y lanza un contenedor temporal **Java 25** para leer la API key de Jackett y configurar una contraseña aleatoria para qBittorrent. Los secretos quedan en `.env` (permisos 600). No monta el socket Docker en el contenedor Java.

Edita `.env` para introducir `TELEGRAM_BOT_TOKEN` y `TELEGRAM_ALLOWED_USER_ID`. Configura al menos un indexador autorizado desde la interfaz de Jackett. No existe forma genérica de adivinar sus credenciales ni de crear un bot de Telegram.

```bash
./start.sh
```

El segundo comando construye la imagen Quarkus con Maven **dentro de Docker** y arranca los tres servicios. Las reconstrucciones posteriores reutilizan la caché de Docker. El bot se reinicia automáticamente gracias a `restart: unless-stopped`.

## Operación

- `./start.sh setup`: inicialización de credenciales; se puede repetir sin reemplazar la contraseña existente.
- `./start.sh`: construir/arrancar el stack completo.
- `./start.sh infra`: arrancar solo Jackett y qBittorrent.
- `./start.sh stop`: detener el stack.
- `./start.sh status`: estado de los servicios.
- `./start.sh logs`: logs del stack.

Los directorios persistentes están en `data/jackett`, `data/qbittorrent` y `data/bot` (SQLite). Las descargas se guardan por defecto en `data/downloads`. Para usar un SSD, define `DOWNLOADS_DIR=/ruta/absoluta` en `.env` antes del primer arranque.

**Importante:** Jackett y qBittorrent solo publican su interfaz en `127.0.0.1` del host. Si administras la Raspberry Pi desde otro equipo, usa un túnel SSH (por ejemplo, `ssh -L 9117:localhost:9117 usuario@raspberry`). El puerto P2P TCP/UDP de qBittorrent sí se publica. Quarkus accede a los servicios por los nombres Docker `jackett` y `qbittorrent`, no por `localhost`.

## Seguridad y límites

- No subas `.env` a Git ni compartas sus valores. No expongas las interfaces web directamente a Internet.
- El inicializador Java usa la contraseña temporal de qBittorrent procedente de sus logs; en instalaciones existentes puedes definir `QBIT_PASSWORD` para verificarla en lugar de sustituirla.
- Si hay contenedores antiguos ocupando 9117/8081, migra sus datos y deténlos antes de desplegar este Compose.
- La validación de URLs del MVP es básica: revisar SSRF antes de ampliar su exposición.
- **Pendiente de validación real:** la disponibilidad de las etiquetas Java 25 para ARM64, el build Quarkus 3.27.1/JDK25 y el bootstrap frente a las versiones de Jackett/qBittorrent. No se ha ejecutado una prueba end-to-end en una Raspberry Pi.
