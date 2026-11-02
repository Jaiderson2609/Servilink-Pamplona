# Pipeline de CI de ServiLink (FPI-25)
- `.github/workflows/ci.yml`: 3 jobs encadenados (linter, pruebas y cobertura >= 90 %, análisis con SonarCloud y Quality Gate).
- `sonar-project.properties`: ajustar `sonar.organization` al crear el proyecto en SonarCloud y guardar `SONAR_TOKEN` en Settings -> Secrets.
- `.github/dependabot.yml`: mantiene al día las versiones de las acciones.
- `backend-ref/`, `negociacion/`, `contrato/`: las suites de las FPI-22 a FPI-24 con la estructura que espera el pipeline.
Verificación local: `ruff check .` | `./negociacion/run.sh` | `cd backend-ref && python -m pytest --cov=servilink_ref --cov-fail-under=90`.
Para que el gate bloquee fusiones: Settings -> Branches -> proteger `main` y exigir los 3 checks.
