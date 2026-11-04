"""Punto de entrada de producción del backend de referencia (FPI-26).

Toda la configuración llega por variables de entorno; nada se escribe en el código ni en la imagen.
Se arranca con: uvicorn --factory servilink_ref.main:crear_aplicacion
"""

import os

from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.engine import URL

from servilink_ref.app import crear_app, crear_motor

LARGO_MINIMO_SECRETO = 32


class ConfiguracionInvalida(RuntimeError):
    """Falta una variable obligatoria o su valor no es aceptable."""


def url_de_base(env):
    """DATABASE_URL tiene prioridad; si no, se arma la URL de PostgreSQL con las variables POSTGRES_*.

    Armarla con URL.create escapa los caracteres especiales de la contraseña.
    """
    if env.get("DATABASE_URL"):
        return env["DATABASE_URL"]
    faltan = [n for n in ("POSTGRES_USER", "POSTGRES_PASSWORD", "POSTGRES_DB") if not env.get(n)]
    if faltan:
        raise ConfiguracionInvalida("Faltan variables de entorno: " + ", ".join(faltan))
    return URL.create(
        "postgresql+psycopg",
        username=env["POSTGRES_USER"],
        password=env["POSTGRES_PASSWORD"],
        host=env.get("POSTGRES_HOST", "db"),
        port=int(env.get("POSTGRES_PORT", "5432")),
        database=env["POSTGRES_DB"],
    )


def validar_secreto(env):
    secreto = env.get("JWT_SECRET", "")
    if len(secreto) < LARGO_MINIMO_SECRETO:
        raise ConfiguracionInvalida(
            f"JWT_SECRET es obligatorio y debe tener al menos {LARGO_MINIMO_SECRETO} caracteres"
        )


def crear_aplicacion(env=None):
    env = os.environ if env is None else env
    validar_secreto(env)
    motor = crear_motor(url_de_base(env))
    app = crear_app(motor)
    app.state.motor = motor

    @app.get("/salud")
    def salud():
        """Usada por el HEALTHCHECK de Docker: responde 200 solo si la base contesta."""
        try:
            with motor.connect() as c:
                c.execute(text("select 1"))
        except Exception:  # noqa: BLE001 - cualquier fallo de base significa «no saludable»
            return JSONResponse(status_code=503, content={"estado": "base no disponible"})
        return {"estado": "ok"}

    return app
