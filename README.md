# ServiLink Pamplona: pipeline de CI y contenerización (FPI-25 y FPI-26)

- `.github/workflows/ci.yml`: 4 jobs encadenados (linter, pruebas y cobertura >= 90 %, análisis con SonarCloud y Quality Gate, e imagen Docker con prueba de humo).
- `backend-ref/Dockerfile`: imagen multietapa, ligera y sin privilegios de root. `backend-ref/.dockerignore` deja fuera pruebas y archivos locales.
- `docker-compose.yml`: API + PostgreSQL con volumen persistente, red interna aislada y credenciales leídas de `.env`.
- `.env.example`: plantilla de variables. Copiar como `.env` (ignorado por Git) y completar.
- `scripts/semilla-demo.sql`: datos de demostración para comprobar la persistencia.
- `backend-ref/`, `negociacion/`, `contrato/`: las suites de las FPI-22 a FPI-24.
- `.github/dependabot.yml`: mantiene al día acciones, imágenes base y dependencias.

Verificación local: `ruff check .` | `bash negociacion/run.sh` | `cd backend-ref && python -m pytest --cov=servilink_ref --cov-fail-under=90`.
Orquestación: `copy .env.example .env` (completar valores) y luego `docker compose up -d --build`.
