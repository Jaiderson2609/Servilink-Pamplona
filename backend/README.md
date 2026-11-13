# ServiLink backend (Spring Boot)

Backend de ServiLink Pamplona: Java 21, Spring Boot 4.1, PostgreSQL, Flyway y JWT (HS256).
Etapa 1: asignación de un técnico (RF-024, RF-025) con bloqueo de fila, libro contable inmutable y bandeja de avisos.

## Requisitos
- JDK 21 (en IntelliJ IDEA: File > Project Structure > SDK > Download JDK > 21).
- PostgreSQL 14 o superior.

## Ejecutar las pruebas
1. Crea la base de pruebas (una sola vez):
   `psql -U postgres -c "CREATE DATABASE servilink_test"`
2. Define la contraseña de tu usuario `postgres` (PowerShell):
   `$env:TEST_POSTGRES_PASSWORD="tu_clave"`
   Si tu servidor no está en `localhost:5432`, define también `TEST_POSTGRES_URL` (por ejemplo `jdbc:postgresql://localhost:5432/servilink_test`) y `TEST_POSTGRES_USER`.
3. `./mvnw test` (en Windows: `mvnw.cmd test`).

Sin PostgreSQL disponible las pruebas de integración se omiten; no fallan.

## Ejecutar la aplicación
Variables de entorno obligatorias: `JWT_SECRET` (mínimo 32 caracteres; sin ella la aplicación no arranca), `DB_URL`, `DB_USER`, `DB_PASSWORD`.
`./mvnw spring-boot:run`

## Decisiones
- Los identificadores son numéricos (int64) para respetar el contrato `contrato/openapi.yaml` vigente; el ERS, el DAD y el DDS usan la misma denominación de identificador numérico.
- El esquema lo crea Flyway (`src/main/resources/db/migration`); Hibernate solo valida (`ddl-auto=validate`).
- El reintento de avisos pendientes corre solo cada 30 s (`servilink.reintentos-cada-ms`).

## Cobertura
`mvn verify` genera el informe de JaCoCo en `target/site/jacoco/index.html` y falla si la cobertura de líneas baja del 90 %. Con PostgreSQL disponible la última medición dio 93,8 % de líneas y 78,9 % de ramas (163 pruebas). Sin PostgreSQL las pruebas de integración se omiten y el umbral falla.

## Canal en tiempo real (WebSocket)
`/ws/v1/eventos` avisa que algo cambió para que el cliente no tenga que consultar la API por intervalos. No lleva datos: el cliente vuelve a pedir lo que necesita por la API REST, que es la que autoriza.

1. El cliente abre la conexión (mismo origen que la aplicación) y en los primeros 5 s envía `{"tipo":"auth","token":"<JWT>"}`. El token no viaja en la URL. Si es inválido, está vencido o la cuenta está suspendida, el servidor cierra con 1008.
2. El servidor responde `{"tipo":"listo"}`. Desde ahí el cliente puede enviar `{"tipo":"ping"}` (responde `{"tipo":"pong"}`) para mantener viva la conexión; las conexiones mudas se cierran a los 120 s.
3. Avisos que recibe el cliente: `{"tipo":"solicitud","id":N}` (el hogar dueño y el técnico asignado, cuando la solicitud cambia de estado, recibe una postulación, se cancela o expira), `{"tipo":"chat","id":N}` (las dos partes, con cada mensaje nuevo) y `{"tipo":"abiertas"}` (todos los técnicos conectados, cuando puede cambiar la lista de solicitudes abiertas).
4. Los avisos salen después del COMMIT: una transacción revertida no avisa.

Límites: la conexión se cierra cuando vence el token (el cliente reconecta con uno vigente), cada usuario mantiene como máximo 5 conexiones (se cierran las más viejas) y los mensajes del cliente no pueden pasar de 8 KB. El registro de conexiones vive en la memoria de la instancia: con varias instancias haría falta un bus compartido (por ejemplo LISTEN/NOTIFY de PostgreSQL). Si el socket se cae, el frontend sigue funcionando por consulta periódica.

## Búsqueda de direcciones (Nominatim)
`GET /api/v1/geocoding/search?q=` (usuario autenticado) convierte una dirección escrita en hasta 5 lugares con coordenadas. El servidor consulta Nominatim (OpenStreetMap) y cumple su política de uso: `User-Agent` que identifica la aplicación, una petición por segundo para todo el servidor, resultados en caché (24 h; 1 h si no hubo coincidencias) y búsqueda solo cuando el usuario toca "Buscar", sin autocompletado. Los resultados se sesgan hacia Pamplona, Cúcuta, Villa del Rosario y Los Patios sin excluir el resto de Colombia. Debe mostrarse la atribución "© colaboradores de OpenStreetMap".

- Texto de 3 a 200 caracteres (`VALIDATION_ERROR` si no).
- `RATE_LIMITED` (429) si hay más de 3 s de búsquedas en espera; `GEOCODING_UNAVAILABLE` (503) si Nominatim no responde. En ambos casos el usuario puede fijar el punto tocando el mapa o escribiendo las coordenadas.
- Variables opcionales: `NOMINATIM_CONTACTO` (un correo o URL de contacto, que se añade al `User-Agent` como pide la política) y `NOMINATIM_URL` (para apuntar a otra instancia).
- La caché y el turno por segundo viven en la memoria de la instancia: con varias instancias cada una respetaría el límite por separado y habría que compartirlos.

## Recargas con Wompi
El técnico recarga su billetera pagando en el Web Checkout de Wompi (Nequi, PSE o tarjeta, en modo de pruebas hasta la entrega). Los datos de pago nunca pasan por este servidor.

1. `POST /api/v1/wallet/topups` `{valorCop}` crea una recarga **PENDING** con una referencia única (`SL-…`) y devuelve `urlPago`: la dirección del checkout con el monto en centavos, la moneda `COP` y la firma de integridad (SHA-256 de `referencia + monto + moneda + secreto de integridad`). No mueve dinero. Máximo 5 intentos por técnico y hora (`RATE_LIMITED`).
2. El cliente lleva al técnico a `urlPago`. Al terminar, Wompi lo devuelve a `URL_PUBLICA/?recarga=<referencia>` y la billetera consulta `GET /api/v1/wallet/topups/{referencia}` hasta que deja de estar pendiente.
3. Wompi llama a `POST /api/v1/webhooks/wompi` (sin JWT). El servidor calcula el SHA-256 de los campos de `signature.properties` (tomados de `data`, en ese orden) + `timestamp` + secreto de eventos y lo compara con `signature.checksum` y con la cabecera `X-Event-Checksum`. Si no coincide: `401 INVALID_SIGNATURE`, saldo sin cambios y una línea de advertencia en el registro.
4. Con firma válida, y bajo bloqueo de la fila de la recarga: `APPROVED` con el monto de la recarga y moneda `COP` inserta un asiento `RECARGA` (con la referencia como identificador del libro), suma al saldo y avisa al técnico, todo en una transacción. Repetir el evento, o recibirlo a la vez varias veces, deja un solo asiento y responde 200.

| Estado | Significado |
| --- | --- |
| `PENDING` | Creada; falta el evento definitivo. |
| `APPROVED` | Acreditada. Es definitiva: ningún evento posterior la revierte. |
| `FAILED` | Rechazada, con error o anulada. No genera movimiento y puede aprobarse si el cliente reintenta el pago con la misma referencia. |
| `REVIEW` | Pago aprobado cuyo monto, moneda o transacción no cuadran. No se acredita sola; queda para revisión manual. |

Wompi no firma el campo `reference` del evento. Por eso una misma transacción de Wompi no puede acreditar dos recargas (índice único sobre `transaccion_wompi`) y el monto del evento se compara con el de la recarga.

Variables de entorno (nunca en el repositorio; sin ellas `POST /wallet/topups` responde `503 PAYMENT_UNAVAILABLE` y todo evento se rechaza):

| Variable | Valor |
| --- | --- |
| `WOMPI_LLAVE_PUBLICA` | Llave pública (`pub_test_…` en pruebas). |
| `WOMPI_SECRETO_INTEGRIDAD` | Secreto de integridad (`test_integrity_…`). |
| `WOMPI_SECRETO_EVENTOS` | Secreto de eventos (`test_events_…`). |
| `URL_PUBLICA` | Dirección pública de la plataforma, a la que regresa el técnico (sin barra final). |
| `WOMPI_CHECKOUT_URL` | Opcional; por defecto `https://checkout.wompi.co/p/`. |

La llave privada de Wompi no se usa: el flujo no consulta su API. En el panel de Wompi, la URL de eventos del ambiente de pruebas es `<URL_PUBLICA>/api/v1/webhooks/wompi`.

`POST /api/v1/wallet/topups/demo` (recarga de demostración, solo con `servilink.recarga-demo-habilitada=true`) sigue existiendo para desarrollo local; en producción debe estar desactivada.

