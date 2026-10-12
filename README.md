# P2P Telegram — MVP local

Quarkus + Jackett (Torznab) + qBittorrent + Telegram. Uso previsto: descargas autorizadas.

## Requisitos
- JDK 25, Maven 3.9+, Jackett y qBittorrent ejecutándose localmente.
- Un bot de Telegram creado mediante @BotFather.
- Obtener tu Telegram user ID y añadirlo a `TELEGRAM_ALLOWED_USER_ID`.

## Arranque
1. Copia `.env.example` a `.env` y completa los valores. **No subas `.env` a Git.**
2. Exporta las variables: `set -a; source .env; set +a` (archivo de confianza, sin espacios alrededor de `=`).
3. Configura qBittorrent Web UI en el puerto indicado por `QBIT_URL`; restringe el acceso a localhost y usa credenciales propias.
4. Configura al menos un indexador permitido en Jackett.
5. Ejecuta `mvn quarkus:dev`.
6. Abre un chat privado con el bot: `/buscar ubuntu`, luego `/descargar 1`.
7. Comprueba la descarga desde la Web UI de qBittorrent.

## Notas y limitaciones
- El MVP utiliza polling, por lo que no requiere webhook ni IP pública.
- No incluye `/descargas` todavía: se consulta el estado desde qBittorrent.
- La selección se guarda en memoria: se pierde al reiniciar Quarkus.
- No incluye reintentos, paginación, persistencia ni verificación de disponibilidad.
- La API de Jackett puede devolver URLs de descarga HTTP de torrents, no solo magnet links.
- La Web UI de qBittorrent debe estar protegida; no la expongas a Internet.
- La validación de URLs es básica. No despliegues este MVP como servicio multiusuario ni expuesto públicamente sin restringir destinos HTTP y revisar SSRF.
- El proyecto se entrega como código fuente inicial; no se ha compilado ni probado en este entorno.
