# Observabilidad local (FPI-29)

Levanta la aplicación con Prometheus (métricas), Grafana (panel) y Jaeger (trazas). Todo queda en `127.0.0.1`.

1. Define las claves en la sesión de PowerShell (no se guardan en ningún archivo):
   `$env:DB_PASSWORD`, `$env:JWT_SECRET` (mín. 32 caracteres), `$env:ADMIN_CLAVE` (mín. 12), `$env:METRICAS_CLAVE` (mín. 16), `$env:GRAFANA_CLAVE`.
2. `docker compose -f docker/observabilidad/docker-compose.yml up --build -d`
3. Tráfico de ejemplo: `docker compose -f docker/observabilidad/docker-compose.yml --profile carga run --rm carga`
4. Panel: http://localhost:3000 (usuario `admin`). Trazas: http://localhost:16686 (servicio `servilink-backend`). Prometheus: http://localhost:9090.
5. Apagar y borrar datos: `docker compose -f docker/observabilidad/docker-compose.yml down -v`

El endpoint crítico observado es `PATCH /api/v1/applications/{id}/select` (asignación del técnico).
