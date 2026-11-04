# syntax=docker/dockerfile:1

# ---------- Etapa 1: constructor (instala las dependencias) ----------
FROM python:3.13-slim AS builder

ENV PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /build

# Primero solo el archivo de dependencias: esta capa se reutiliza (caché) mientras
# requirements-prod.txt no cambie, aunque el código fuente sí cambie.
COPY requirements-prod.txt .
RUN pip install --prefix=/install -r requirements-prod.txt

# ---------- Etapa 2: imagen final (solo lo necesario para ejecutar) ----------
FROM python:3.13-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# Usuario sin privilegios, sin contraseña ni shell de inicio de sesión
RUN groupadd --system --gid 10001 servilink \
 && useradd --system --uid 10001 --gid servilink --no-create-home --shell /usr/sbin/nologin servilink

WORKDIR /app

# Solo se copian las dependencias ya instaladas y el código de la aplicación
COPY --from=builder /install /usr/local
COPY --chown=servilink:servilink servilink_ref ./servilink_ref

USER 10001:10001

EXPOSE 8000

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
  CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/salud', timeout=2)"]

CMD ["uvicorn", "--factory", "servilink_ref.main:crear_aplicacion", "--host", "0.0.0.0", "--port", "8000"]
