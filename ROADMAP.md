# Roadmap

## P1: Operacion segura

1. Implementar `/descargas` consultando qBittorrent y mostrar progreso, estado, velocidad y ETA. En curso.
2. Clasificar errores HTTP, hacer configurables los timeouts y aplicar backoff para fallos transitorios. Completado.
3. Persistir las selecciones de busqueda con TTL.
4. Reconciliar descargas ambiguas comprobando el infohash en qBittorrent tras un reinicio.
5. Crear Docker Compose con red privada, volumenes y healthchecks.

## P2: Calidad y despliegue

1. Paginar resultados y descargas en Telegram.
2. Reutilizar y renovar la sesion de qBittorrent.
3. Anadir Maven Wrapper, Enforcer, formato y analisis estatico.
4. Anadir CI de GitHub Actions para `mvn package`.
5. Crear un perfil de despliegue y medir recursos en Raspberry Pi.
