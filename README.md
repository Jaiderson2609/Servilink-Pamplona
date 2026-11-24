# ServiLink Pamplona

Plataforma web geolocalizada que conecta hogares con técnicos independientes de Cúcuta, Villa del Rosario y Los Patios. El hogar publica su problema con fotos y ubicación, los técnicos cercanos se postulan con un precio, se negocia y el hogar elige. La plataforma cobra una comisión desde una billetera prepago del técnico.

Proyecto académico de Ingeniería de Sistemas, Universidad de Pamplona (Ingeniería de Software II). Autor: Jaider Junior Solano Hernández. Docente: Ing. Miguel Eduardo Posada Haddad.

- **Producción:** https://servilink-pamplona.onrender.com (plan gratuito de Render: la primera petición después de un rato sin uso puede tardar hasta un minuto).
- **Versión:** `v1.0.0`. El commit desplegado se consulta en `/actuator/info`.

## Cómo funciona

1. El hogar publica una solicitud: oficio, descripción, hasta 3 fotos y su ubicación en el mapa.
2. Los técnicos verificados y disponibles a menos de 5 km reciben el aviso y se postulan con un precio durante 10 minutos.
3. Se negocia el precio: hasta 2 ofertas del técnico y 1 contraoferta del hogar. Desde la primera postulación, el hogar tiene 5 minutos para elegir.
4. Al elegir, el sistema retiene la comisión (10 %) de la billetera del técnico, abre el chat y le muestra la ubicación exacta al técnico elegido. Antes de eso, los técnicos solo ven una zona aproximada (círculo de 500 m).
5. El técnico marca su llegada y el trabajo terminado; el hogar confirma. La comisión se descuenta y ambos se califican.

Estados de una solicitud: E01 abierta, E02 con postulaciones, E03 asignada, E04 en ejecución, E05 finalizada, E06 expirada, E07 cancelada. Mientras tiene un servicio activo, el técnico aparece como "Ocupado".

## Tecnologías

| Capa | Tecnología |
| --- | --- |
| Frontend | React 19, Vite 8, PWA instalable, Leaflet con OpenStreetMap |
| Backend | Java 21, Spring Boot 4.1 (monolito modular), Spring Security con JWT, WebSocket |
| Datos | PostgreSQL con PostGIS (Supabase en producción), migraciones Flyway |
| Integraciones | Firebase Cloud Messaging (push), Cloudinary (fotos), Wompi en modo de pruebas (recargas), Nominatim (direcciones), Google (inicio de sesión) |
| Operación | Docker (una imagen con la PWA y la API), Render, GitHub Actions |
| Observabilidad | Actuator, métricas de Prometheus, trazas OpenTelemetry y registros estructurados |

## Estructura del repositorio

- `backend/`: API Spring Boot, migraciones Flyway (`V1` a `V13`) y pruebas.
- `frontend/`: aplicación React (PWA) y sus pruebas con Vitest.
- `docker/`: `Dockerfile.app` (compila el frontend y el backend en una sola imagen) y configuración de observabilidad.
- `docker-compose.app.yml`: aplicación completa con PostGIS para ejecutar en local.
- `scripts/semilla-demo.sql`: datos de demostración.
- `backend-ref/`, `negociacion/`, `contrato/`: suites de referencia de las guías del curso (FPI-22 a FPI-24).
- `.github/workflows/`: integración continua, seguridad (DevSecOps) y despliegue continuo.

## Ejecutar en local

Requisitos: Docker. Defina las variables sin escribirlas en el repositorio (en PowerShell):

```
$env:DB_PASSWORD="elija-una-clave"
$env:JWT_SECRET="una-cadena-aleatoria-de-32-caracteres-o-mas"
$env:ADMIN_CLAVE="clave-del-administrador"
docker compose -f docker-compose.app.yml up --build
```

La aplicación queda en http://localhost:8080. Las llaves de Wompi, Cloudinary, Firebase y Google son opcionales en local: sin ellas esas funciones quedan desactivadas. Las variables disponibles están en `backend/src/main/resources/application.properties`.

## Pruebas

- **Backend:** `cd backend` y `./mvnw test`. Las pruebas de integración usan PostgreSQL con PostGIS (variable `DB_URL`).
- **Frontend:** `cd frontend`, `npm ci` y `npm test`.
- **Integración continua:** cada Pull Request ejecuta el linter, las pruebas con cobertura, el análisis estático y la construcción de la imagen. La fusión exige que pasen las 9 verificaciones obligatorias, incluidas las de seguridad (análisis estático y de dependencias).

## Despliegue

Al integrar en `main`, el flujo "Despliegue continuo" construye la imagen, la publica y la despliega en Render. Las migraciones de Flyway se aplican al arrancar. Los secretos se configuran como variables de entorno del proveedor y nunca se guardan en el repositorio.

## Alcance de la versión 1.0.0

Quedan para una fase posterior: la confirmación automática del servicio a las 24 horas, el ajuste de precio en sitio y las incidencias, los reportes de usuarios y el ocultamiento de reseñas, la anonimización de cuentas, la medición de carga y de accesibilidad, y la aplicación móvil nativa. Las notificaciones push con el navegador cerrado dependen del dispositivo (ahorro de batería y ajustes del fabricante).

## Referencias del curso: CI y contenerización (FPI-25 y FPI-26)

- `.github/workflows/ci.yml`: 4 jobs encadenados (linter, pruebas y cobertura >= 90 %, análisis con SonarCloud y Quality Gate, e imagen Docker con prueba de humo).
- `backend-ref/Dockerfile`: imagen multietapa, ligera y sin privilegios de root.
- `docker-compose.yml`: API de referencia y PostgreSQL con volumen persistente. Copiar `.env.example` como `.env` y completar.
- `.github/dependabot.yml`: mantiene al día acciones, imágenes base y dependencias.

Verificación local de las suites de referencia: `ruff check .` | `bash negociacion/run.sh` | `cd backend-ref && python -m pytest --cov=servilink_ref --cov-fail-under=90`.
