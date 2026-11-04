"""Pruebas del incremento II-03: bandeja de salida de avisos y asignación simultánea.

La bandeja se prueba con SQLite en memoria (como el resto de la suite). La concurrencia exige un
motor con bloqueo de filas real: se ejecuta solo si TEST_POSTGRES_URL apunta a un PostgreSQL de
prueba y se omite (skip) en caso contrario, para no fingir una comprobación que no se hizo.
"""

import os
import threading
from datetime import UTC, datetime, timedelta

import httpx
import pytest
from conftest import token
from fastapi.testclient import TestClient
from servilink_ref.app import (
    FCM_URL,
    Billetera,
    Movimiento,
    NotificacionPendiente,
    Postulacion,
    Solicitud,
    crear_app,
    crear_motor,
)
from sqlalchemy import text
from sqlalchemy.orm import sessionmaker

URL = "/api/v1/solicitudes/1/asignacion"
CUERPO = {"postulacionId": 11}


def estados(entorno):
    with entorno.S() as s:
        return [
            (n.estado, n.intentos) for n in s.query(NotificacionPendiente).order_by(NotificacionPendiente.id)
        ]


def test_aviso_entregado_queda_enviado(entorno):
    assert entorno.cliente.post(URL, json=CUERPO, headers=token()).status_code == 200
    assert estados(entorno) == [("ENVIADA", 1)]


def test_si_firebase_falla_el_aviso_queda_pendiente_y_se_reintenta(entorno):
    entorno.fcm.post(FCM_URL).mock(side_effect=httpx.ConnectError("sin red"))
    assert entorno.cliente.post(URL, json=CUERPO, headers=token()).status_code == 200
    assert estados(entorno) == [("PENDIENTE", 1)]  # la asignación se conservó y el aviso no se perdió
    entorno.fcm.post(FCM_URL).respond(200)  # Firebase se recupera
    assert entorno.cliente.app.state.reintentar_pendientes() == 1
    assert estados(entorno) == [("ENVIADA", 2)]
    assert entorno.cliente.app.state.reintentar_pendientes() == 0  # ya no hay nada que reenviar


def test_una_respuesta_de_error_de_firebase_tambien_cuenta_como_fallo(entorno):
    entorno.fcm.post(FCM_URL).respond(503)
    entorno.cliente.post(URL, json=CUERPO, headers=token())
    assert estados(entorno) == [("PENDIENTE", 1)]


def test_el_reintento_http_del_hogar_no_duplica_el_aviso(entorno):
    for _ in range(3):
        entorno.cliente.post(URL, json=CUERPO, headers=token())
    assert estados(entorno) == [("ENVIADA", 1)]
    assert entorno.fcm.calls.call_count == 1


POSTGRES = os.getenv("TEST_POSTGRES_URL")


@pytest.fixture
def pg():
    motor = crear_motor(POSTGRES)
    app = crear_app(motor, reloj=lambda: datetime.now(UTC))
    S = sessionmaker(bind=motor, expire_on_commit=False)
    with motor.begin() as c:
        for t in (
            "notificaciones_pendientes",
            "libro_contable",
            "postulaciones",
            "solicitudes",
            "billeteras",
        ):
            c.execute(text(f"delete from {t}"))
    with S.begin() as s:
        s.add(Solicitud(id=1, hogar_id=7, estado="E02", expira_en=datetime.now(UTC) + timedelta(minutes=5)))
        s.flush()
        for i, tid in enumerate((21, 22)):
            s.add(Billetera(tecnico_id=tid, disponible=24000, retenido=0, nombre=f"T{tid}", telefono="3"))
            s.add(Postulacion(id=11 + i, solicitud_id=1, tecnico_id=tid, precio=60000))
    yield app, S
    motor.dispose()


def simultaneas(app, postulaciones):
    barrera, codigos = threading.Barrier(len(postulaciones)), []

    def enviar(p):
        cliente = TestClient(app, raise_server_exceptions=False)
        barrera.wait()
        codigos.append(cliente.post(URL, json={"postulacionId": p}, headers=token()).status_code)

    hilos = [threading.Thread(target=enviar, args=(p,)) for p in postulaciones]
    [h.start() for h in hilos]
    [h.join() for h in hilos]
    return sorted(codigos)


@pytest.mark.skipif(not POSTGRES, reason="requiere TEST_POSTGRES_URL (PostgreSQL de prueba)")
@pytest.mark.parametrize("intento", range(10))
def test_dos_tecnicos_simultaneos_uno_gana_y_el_otro_recibe_409(pg, intento):
    app, S = pg
    with pytest.MonkeyPatch.context() as m:
        m.setattr("servilink_ref.app.FCM_URL", "http://127.0.0.1:9/inalcanzable")
        assert simultaneas(app, [11, 12]) == [200, 409]  # antes: [200, 500]
    with S() as s:
        assert s.get(Solicitud, 1).estado == "E03"
        assert sum(24000 - s.get(Billetera, t).disponible for t in (21, 22)) == 6000  # una sola comisión
        assert s.query(Movimiento).count() == 1


@pytest.mark.skipif(not POSTGRES, reason="requiere TEST_POSTGRES_URL (PostgreSQL de prueba)")
@pytest.mark.parametrize("intento", range(10))
def test_reintento_simultaneo_del_mismo_tecnico_responde_200_dos_veces(pg, intento):
    app, S = pg
    assert simultaneas(app, [11, 11]) == [200, 200]  # antes: [200, 500]
    with S() as s:
        assert s.query(Movimiento).count() == 1
