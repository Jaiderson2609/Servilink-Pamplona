import hashlib
import sqlite3
import time
from datetime import UTC, datetime, timedelta
from pathlib import Path

import jwt
import pytest
import respx
import yaml
from fastapi.testclient import TestClient
from servilink_ref.app import FCM_URL, JWT_SECRET, Billetera, Postulacion, Solicitud, crear_app, crear_motor
from sqlalchemy.orm import sessionmaker

AHORA = datetime(2026, 11, 1, 15, 0, tzinfo=UTC)
RAIZ = Path(__file__).resolve().parent.parent
DEV_DB = RAIZ / "servilink_dev.db"  # base "de desarrollo" que ninguna prueba debe tocar


def pytest_sessionstart(session):
    c = sqlite3.connect(DEV_DB)
    c.execute("create table if not exists clientes(id integer primary key, nombre text)")
    c.execute("insert or ignore into clientes values (1,'dato real de desarrollo')")
    c.commit()
    c.close()
    session.config._huella = hashlib.sha256(DEV_DB.read_bytes()).hexdigest()


def pytest_sessionfinish(session, exitstatus):
    despues = hashlib.sha256(DEV_DB.read_bytes()).hexdigest()
    assert despues == session.config._huella, "¡Una prueba escribió en la base de desarrollo!"
    print("\n[aislamiento] La base de desarrollo quedó idéntica (SHA-256 sin cambios).")


def token(rol="HOGAR", sub=7, vigente=True):
    exp = int(time.time()) + (900 if vigente else -10)
    return {
        "Authorization": "Bearer "
        + jwt.encode({"sub": str(sub), "rol": rol, "exp": exp}, JWT_SECRET, algorithm="HS256")
    }


@pytest.fixture
def contrato():
    return yaml.safe_load((RAIZ.parent / "contrato" / "openapi-referencia.yaml").read_text(encoding="utf8"))


@pytest.fixture
def fcm():
    """Frontera de infraestructura: cualquier llamada HTTP no simulada falla la prueba."""
    with respx.mock(assert_all_mocked=True, assert_all_called=False) as m:
        m.post(FCM_URL).respond(200, json={"name": "projects/servilink/messages/1"})
        yield m


@pytest.fixture
def entorno(fcm):
    """BD efímera en memoria: se crea y se destruye en cada prueba."""
    motor = crear_motor()
    reloj = {"ahora": AHORA}
    app = crear_app(motor, reloj=lambda: reloj["ahora"])
    S = sessionmaker(bind=motor, expire_on_commit=False)
    with S.begin() as s:
        s.add(Solicitud(id=1, hogar_id=7, estado="E02", expira_en=AHORA + timedelta(minutes=5)))
        s.add(
            Billetera(
                tecnico_id=21,
                disponible=24000,
                retenido=0,
                nombre="Carlos",
                telefono="+57 300 000 0000",
                fcm_token="tok-carlos-0123456789",
            )
        )
        s.add(Postulacion(id=11, solicitud_id=1, tecnico_id=21, precio=60000))

    class E:
        pass

    e = E()
    e.cliente = TestClient(app)
    e.S = S
    e.reloj = reloj
    e.motor = motor
    e.fcm = fcm
    yield e
    motor.dispose()
