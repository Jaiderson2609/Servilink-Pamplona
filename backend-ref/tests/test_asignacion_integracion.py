"""Pruebas de integración de POST /api/v1/solicitudes/{id}/asignacion (contrato FPI-22).

Componentes reales: HTTP, JWT, lógica de negocio y base de datos (SQLite en memoria).
Aislado en la frontera: Firebase FCM (respx). El reloj se inyecta para controlar la ventana.
"""

from datetime import timedelta

import jsonschema
import pytest
from conftest import token
from servilink_ref.app import Billetera, Movimiento, Solicitud

URL = "/api/v1/solicitudes/1/asignacion"
CUERPO = {"postulacionId": 11}


def valida(contrato, nombre, instancia):
    esquema = {"$ref": f"#/components/schemas/{nombre}", "components": contrato["components"]}
    jsonschema.validate(instancia, esquema)


def test_asignacion_correcta_cumple_contrato_y_persiste(entorno, contrato):
    r = entorno.cliente.post(URL, json=CUERPO, headers=token())
    assert r.status_code == 200
    valida(contrato, "AsignacionResponse", r.json())  # contrato OpenAPI
    assert r.json()["comisionRetenidaCop"] == 6000  # 10 % de 60.000
    with entorno.S() as s:  # estado real en la BD
        assert s.get(Solicitud, 1).estado == "E03"
        b = s.get(Billetera, 21)
        assert (b.disponible, b.retenido) == (18000, 6000)
        assert s.query(Movimiento).count() == 1
    assert entorno.fcm.calls.call_count == 1  # se avisó una vez al técnico


def test_reintento_es_idempotente_y_no_cobra_dos_veces(entorno):
    for _ in range(3):
        assert entorno.cliente.post(URL, json=CUERPO, headers=token()).status_code == 200
    with entorno.S() as s:
        assert s.get(Billetera, 21).retenido == 6000
        assert s.query(Movimiento).count() == 1
    assert entorno.fcm.calls.call_count == 1


def test_ventana_expirada_responde_409_y_no_cambia_nada(entorno, contrato):
    entorno.reloj["ahora"] += timedelta(minutes=6)
    r = entorno.cliente.post(URL, json=CUERPO, headers=token())
    assert r.status_code == 409 and r.headers["content-type"].startswith("application/problem+json")
    valida(contrato, "Problema", r.json())
    with entorno.S() as s:
        assert s.get(Solicitud, 1).estado == "E02"
        assert s.get(Billetera, 21).disponible == 24000


def test_saldo_insuficiente_responde_422(entorno):
    with entorno.S.begin() as s:
        s.get(Billetera, 21).disponible = 5999
    assert entorno.cliente.post(URL, json=CUERPO, headers=token()).status_code == 422
    with entorno.S() as s:
        assert s.get(Solicitud, 1).estado == "E02" and s.query(Movimiento).count() == 0


def test_solicitud_en_otro_estado_responde_409(entorno):
    with entorno.S.begin() as s:
        s.get(Solicitud, 1).estado = "E07"
    assert entorno.cliente.post(URL, json=CUERPO, headers=token()).status_code == 409


@pytest.mark.parametrize(
    "encabezados,esperado",
    [
        ({}, 401),  # sin token
        (token(vigente=False), 401),  # token vencido
        (token(rol="TECNICO"), 403),  # rol equivocado
        (token(sub=99), 403),  # hogar que no es el dueño
    ],
)
def test_seguridad_del_contrato(entorno, encabezados, esperado):
    assert entorno.cliente.post(URL, json=CUERPO, headers=encabezados).status_code == esperado
    with entorno.S() as s:
        assert s.get(Solicitud, 1).estado == "E02"


def test_solicitud_inexistente_responde_404(entorno):
    assert (
        entorno.cliente.post("/api/v1/solicitudes/999/asignacion", json=CUERPO, headers=token()).status_code
        == 404
    )


def test_cuerpo_invalido_responde_400(entorno):
    assert entorno.cliente.post(URL, json={}, headers=token()).status_code == 400


def test_si_firebase_falla_la_asignacion_se_mantiene(entorno):
    entorno.fcm.routes[0].respond(500)  # Firebase caído (simulado)
    assert entorno.cliente.post(URL, json=CUERPO, headers=token()).status_code == 200
    with entorno.S() as s:
        assert s.get(Solicitud, 1).estado == "E03"


def test_el_teléfono_solo_aparece_en_el_contacto(entorno):
    r = entorno.cliente.post(URL, json=CUERPO, headers=token())
    cuerpo = r.json()
    assert "telefono" in cuerpo["contacto"]
    assert "cedula" not in r.text and "direccion" not in r.text


def test_la_base_usada_es_efimera_en_memoria(entorno):
    assert str(entorno.motor.url) == "sqlite://"


def test_frontera_justo_en_el_instante_de_expiracion_responde_409(entorno):
    """Regla: la ventana vale mientras reloj < expira_en; en el instante exacto ya expiró."""
    entorno.reloj["ahora"] = entorno.reloj["ahora"] + timedelta(minutes=5)  # == expira_en
    assert entorno.cliente.post(URL, json=CUERPO, headers=token()).status_code == 409


def test_frontera_un_segundo_antes_de_expirar_si_asigna(entorno):
    entorno.reloj["ahora"] = entorno.reloj["ahora"] + timedelta(minutes=5, seconds=-1)
    assert entorno.cliente.post(URL, json=CUERPO, headers=token()).status_code == 200


def test_frontera_saldo_igual_a_la_comision_si_asigna_y_queda_en_cero(entorno):
    with entorno.S.begin() as s:
        s.get(Billetera, 21).disponible = 6000  # == comisión del 10 % de 60.000
    assert entorno.cliente.post(URL, json=CUERPO, headers=token()).status_code == 200
    with entorno.S() as s:
        assert (s.get(Billetera, 21).disponible, s.get(Billetera, 21).retenido) == (0, 6000)


def test_frontera_saldo_un_peso_menor_que_la_comision_responde_422(entorno):
    with entorno.S.begin() as s:
        s.get(Billetera, 21).disponible = 5999
    assert entorno.cliente.post(URL, json=CUERPO, headers=token()).status_code == 422
