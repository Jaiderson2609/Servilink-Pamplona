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

