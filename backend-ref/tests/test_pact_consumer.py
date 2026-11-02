"""Lado CONSUMIDOR del contrato: la PWA declara qué espera de la API y se genera el archivo Pact."""

from pathlib import Path

import httpx
import jwt
from pact import Pact
from servilink_ref.app import JWT_SECRET

PACTS = Path(__file__).resolve().parent.parent / "pacts"
# Token de prueba con vigencia fija para que el archivo Pact sea estable.
TOKEN = jwt.encode({"sub": "7", "rol": "HOGAR", "exp": 4102444800}, JWT_SECRET, algorithm="HS256")


def test_pwa_espera_esta_respuesta_al_asignar():
    pact = Pact("pwa-servilink", "api-servilink").with_specification("V4")
    (
        pact.upon_receiving("asignar un técnico a una solicitud en E02")
        .given("la solicitud 1 está en E02 y el técnico 21 tiene saldo")
        .with_request("POST", "/api/v1/solicitudes/1/asignacion")
        .with_header("Content-Type", "application/json")
        .with_header("Authorization", f"Bearer {TOKEN}")
        .with_body({"postulacionId": 11})
        .will_respond_with(200)
        .with_body(
            {
                "solicitudId": 1,
                "estado": "E03",
                "comisionRetenidaCop": 6000,
                "contacto": {"nombre": "Carlos", "telefono": "+57 300 000 0000"},
            }
        )
    )
    with pact.serve() as servidor:  # servidor simulado del proveedor
        r = httpx.post(
            f"{servidor.url}/api/v1/solicitudes/1/asignacion",
            json={"postulacionId": 11},
            headers={"Authorization": f"Bearer {TOKEN}"},
        )
        assert r.status_code == 200 and r.json()["estado"] == "E03"
    PACTS.mkdir(exist_ok=True)
    pact.write_file(PACTS)
