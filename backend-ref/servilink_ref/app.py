"""Implementación de referencia del endpoint POST /solicitudes/{id}/asignacion.

Sirve para verificar el contrato OpenAPI (FPI-22) con pruebas de integración mientras
el controlador Spring Boot no existe. Aplica las reglas del ADR-04: retención del 10 %,
ventana de postulación, estados y notificación como puerto de infraestructura.
"""

import os
from datetime import UTC, datetime
from decimal import ROUND_HALF_UP, Decimal

import httpx
import jwt
from fastapi import FastAPI, Header
from fastapi.responses import JSONResponse
from sqlalchemy import Column, DateTime, ForeignKey, Integer, String, create_engine
from sqlalchemy.orm import declarative_base, sessionmaker
from sqlalchemy.pool import StaticPool

JWT_SECRET = os.getenv("JWT_SECRET", "clave-de-desarrollo-no-usar-en-produccion-0123456789")
FCM_URL = os.getenv("FCM_URL", "https://fcm.googleapis.com/v1/projects/servilink/messages:send")
COMISION = Decimal("0.10")

Base = declarative_base()


class Solicitud(Base):
    __tablename__ = "solicitudes"
    id = Column(Integer, primary_key=True)
    hogar_id = Column(Integer, nullable=False)
    estado = Column(String(3), nullable=False)
    expira_en = Column(DateTime(timezone=True), nullable=False)
    tecnico_id = Column(Integer)
    precio_acordado = Column(Integer)


class Postulacion(Base):
    __tablename__ = "postulaciones"
    id = Column(Integer, primary_key=True)
    solicitud_id = Column(Integer, ForeignKey("solicitudes.id"), nullable=False)
    tecnico_id = Column(Integer, nullable=False)
    precio = Column(Integer, nullable=False)


class Billetera(Base):
    __tablename__ = "billeteras"
    tecnico_id = Column(Integer, primary_key=True)
    disponible = Column(Integer, nullable=False, default=0)
    retenido = Column(Integer, nullable=False, default=0)
    nombre = Column(String(60), nullable=False)
    telefono = Column(String(20), nullable=False)
    fcm_token = Column(String(200))


class Movimiento(Base):
    """Libro contable inmutable: solo se insertan filas."""

    __tablename__ = "libro_contable"
    id = Column(Integer, primary_key=True)
    tecnico_id = Column(Integer, nullable=False)
    tipo = Column(String(12), nullable=False)
    valor = Column(Integer, nullable=False)
    referencia = Column(String(40), nullable=False, unique=True)
    fecha = Column(DateTime(timezone=True), nullable=False)


def crear_motor(url="sqlite://"):
    """Por defecto, SQLite en memoria: efímera y aislada de cualquier base real."""
    return create_engine(url, connect_args={"check_same_thread": False}, poolclass=StaticPool)


def reloj_real():
    return datetime.now(UTC)


def problema(status, titulo, detalle=""):
    return JSONResponse(
        status_code=status,
        media_type="application/problem+json",
        content={
            "type": "https://servilink.co/errores/" + titulo.lower().replace(" ", "-"),
            "title": titulo,
            "status": status,
            "detail": detalle,
        },
    )


def crear_app(motor, reloj=reloj_real, http_factory=lambda: httpx.Client(timeout=3.0)):
    Base.metadata.create_all(motor)
    Sesion = sessionmaker(bind=motor, expire_on_commit=False)
    app = FastAPI()

    def notificar(token, texto):
        """Puerto de salida hacia Firebase: es el único punto que toca la red."""
        if not token:
            return
        try:
            with http_factory() as c:
                c.post(FCM_URL, json={"message": {"token": token, "notification": {"body": texto}}})
        except httpx.HTTPError:
            pass  # la notificación es de mejor esfuerzo: no revierte la asignación

    @app.post("/api/v1/solicitudes/{solicitud_id}/asignacion")
    def asignar(solicitud_id: int, cuerpo: dict, authorization: str = Header(default="")):
        try:
            claims = jwt.decode(authorization.removeprefix("Bearer "), JWT_SECRET, algorithms=["HS256"])
        except jwt.PyJWTError:
            return problema(401, "No autenticado", "Token ausente, vencido o inválido")
        if claims.get("rol") != "HOGAR":
            return problema(403, "Sin permiso", "Solo un hogar puede asignar")
        if not isinstance(cuerpo.get("postulacionId"), int):
            return problema(400, "Solicitud invalida", "postulacionId es obligatorio")
        with Sesion() as s, s.begin():
            sol = s.get(Solicitud, solicitud_id)
            if sol is None:
                return problema(404, "No encontrado")
            if sol.hogar_id != int(claims["sub"]):
                return problema(403, "Sin permiso", "La solicitud es de otro hogar")
            pos = s.get(Postulacion, cuerpo["postulacionId"])
            if pos is None or pos.solicitud_id != sol.id:
                return problema(404, "No encontrado", "Postulación inexistente")
            bil = s.get(Billetera, pos.tecnico_id)
            comision = int((Decimal(pos.precio) * COMISION).quantize(Decimal("1"), ROUND_HALF_UP))
            if sol.estado == "E03" and sol.tecnico_id == pos.tecnico_id:  # reintento idempotente
                return _respuesta(sol, bil, comision)
            if sol.estado != "E02":
                return problema(409, "Estado invalido", f"La solicitud está en {sol.estado}")
            if reloj() >= _aware(sol.expira_en):
                return problema(409, "Ventana expirada", "La ventana de selección terminó")
            if bil.disponible < comision:
                return problema(422, "Saldo insuficiente", "No alcanza para retener la comisión")
            bil.disponible -= comision
            bil.retenido += comision
            s.add(
                Movimiento(
                    tecnico_id=bil.tecnico_id,
                    tipo="RETENCION",
                    valor=comision,
                    referencia=f"ASIG-{sol.id}",
                    fecha=reloj(),
                )
            )
            sol.estado, sol.tecnico_id, sol.precio_acordado = "E03", bil.tecnico_id, pos.precio
            token = bil.fcm_token
        notificar(token, "Fuiste asignado a una solicitud")
        with Sesion() as s:
            return _respuesta(s.get(Solicitud, solicitud_id), s.get(Billetera, pos.tecnico_id), comision)

    def _respuesta(sol, bil, comision):
        return {
            "solicitudId": sol.id,
            "estado": "E03",
            "comisionRetenidaCop": comision,
            "contacto": {"nombre": bil.nombre, "telefono": bil.telefono},
        }

    return app


def _aware(d):
    return d if d.tzinfo else d.replace(tzinfo=UTC)
