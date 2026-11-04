"""Configuración de producción: falla rápido si falta algo y expone /salud para el HEALTHCHECK."""

import pytest
from fastapi.testclient import TestClient
from servilink_ref.main import ConfiguracionInvalida, crear_aplicacion, url_de_base

SECRETO = "s" * 40
BASE_OK = {"JWT_SECRET": SECRETO, "DATABASE_URL": "sqlite://"}


def test_salud_responde_ok_con_la_base_disponible():
    with TestClient(crear_aplicacion(BASE_OK)) as c:
        r = c.get("/salud")
    assert r.status_code == 200
    assert r.json() == {"estado": "ok"}


def test_salud_responde_503_si_la_base_no_contesta(monkeypatch):
    app = crear_aplicacion(BASE_OK)

    def sin_conexion():
        raise OSError("sin conexion")

    monkeypatch.setattr(app.state.motor, "connect", sin_conexion)
    with TestClient(app) as c:
        r = c.get("/salud")
    assert r.status_code == 503
    assert r.json() == {"estado": "base no disponible"}


@pytest.mark.parametrize(
    "env", [{}, {"DATABASE_URL": "sqlite://"}, {"JWT_SECRET": "corto", "DATABASE_URL": "sqlite://"}]
)
def test_sin_secreto_valido_la_aplicacion_no_arranca(env):
    with pytest.raises(ConfiguracionInvalida, match="JWT_SECRET"):
        crear_aplicacion(env)


def test_sin_datos_de_base_no_arranca():
    with pytest.raises(ConfiguracionInvalida, match="POSTGRES_PASSWORD"):
        crear_aplicacion({"JWT_SECRET": SECRETO, "POSTGRES_USER": "u", "POSTGRES_DB": "d"})


def test_la_url_de_postgresql_escapa_la_contrasena():
    url = url_de_base({"POSTGRES_USER": "servilink", "POSTGRES_PASSWORD": "p@ss:/word", "POSTGRES_DB": "sl"})
    texto = url.render_as_string(hide_password=False)
    assert texto == "postgresql+psycopg://servilink:p%40ss%3A%2Fword@db:5432/sl"
    assert "p@ss" not in repr(url)  # la contraseña tampoco aparece en el repr (se enmascara)


def test_database_url_tiene_prioridad():
    assert url_de_base({"DATABASE_URL": "sqlite:///x.db", "POSTGRES_USER": "u"}) == "sqlite:///x.db"


def test_el_motor_de_postgresql_se_crea_sin_conectar():
    from servilink_ref.app import crear_motor

    motor = crear_motor("postgresql+psycopg://u:p@db:5432/d")
    assert motor.dialect.name == "postgresql"
    assert motor.pool._pre_ping is True
