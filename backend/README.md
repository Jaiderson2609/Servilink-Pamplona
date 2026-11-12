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
