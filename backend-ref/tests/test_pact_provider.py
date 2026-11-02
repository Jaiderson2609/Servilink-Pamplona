"""Lado PROVEEDOR: la API real (con BD en memoria) debe cumplir lo que la PWA pactó."""

import threading
import time
from datetime import timedelta
from pathlib import Path

import uvicorn
from conftest import AHORA
from pact import Verifier
from servilink_ref.app import Base, Billetera, Postulacion, Solicitud, crear_app, crear_motor
from sqlalchemy.orm import sessionmaker

PACTS = Path(__file__).resolve().parent.parent / "pacts"


def test_la_api_cumple_el_pacto_de_la_pwa():
    motor = crear_motor()
    app = crear_app(motor, reloj=lambda: AHORA)
    S = sessionmaker(bind=motor, expire_on_commit=False)

    def estado_inicial(*args, **kwargs):  # prepara el estado que pide el consumidor
        Base.metadata.drop_all(motor)
        Base.metadata.create_all(motor)
        with S.begin() as s:
            s.add(Solicitud(id=1, hogar_id=7, estado="E02", expira_en=AHORA + timedelta(minutes=5)))
            s.add(
                Billetera(
                    tecnico_id=21, disponible=24000, retenido=0, nombre="Carlos", telefono="+57 300 000 0000"
                )
            )
            s.add(Postulacion(id=11, solicitud_id=1, tecnico_id=21, precio=60000))

    servidor = uvicorn.Server(uvicorn.Config(app, host="localhost", port=8765, log_level="error"))
    hilo = threading.Thread(target=servidor.run, daemon=True)
    hilo.start()
    while not servidor.started:
        time.sleep(0.05)
    try:
        v = (
            Verifier("api-servilink")
            .add_transport(url="http://localhost:8765")
            .add_source(str(PACTS / "pwa-servilink-api-servilink.json"))
            .state_handler(estado_inicial, teardown=False)
        )
        v.verify()
    finally:
        servidor.should_exit = True
        hilo.join(timeout=5)
