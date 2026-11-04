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


class NotificacionPendiente(Base):
    """Bandeja de salida (outbox): se guarda en la misma transacción que la asignación.

    Si Firebase falla o el proceso muere tras confirmar, el aviso queda PENDIENTE y se reintenta.
    La clave única evita avisar dos veces por la misma asignación.
    """

    __tablename__ = "notificaciones_pendientes"
    id = Column(Integer, primary_key=True)
    clave = Column(String(40), nullable=False, unique=True)
    tecnico_id = Column(Integer, nullable=False)
    texto = Column(String(200), nullable=False)
    estado = Column(String(10), nullable=False, default="PENDIENTE")
    intentos = Column(Integer, nullable=False, default=0)
    creada_en = Column(DateTime(timezone=True), nullable=False)


def crear_motor(url="sqlite://"):
    """Por defecto, SQLite en memoria: efímera y aislada de cualquier base real.

    Con cualquier otra URL (PostgreSQL en Docker Compose) se usa un pool normal que verifica la
    conexión antes de entregarla, para tolerar que la base reinicie.
    """
    if str(url).startswith("sqlite"):
        return create_engine(url, connect_args={"check_same_thread": False}, poolclass=StaticPool)
    return create_engine(url, pool_pre_ping=True)


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
        """Puerto de salida hacia Firebase: es el único punto que toca la red. True si se entregó."""
        if not token:
            return True  # sin token no hay a quién avisar: nada que reintentar
        try:
            with http_factory() as c:
                carga = {"message": {"token": token, "notification": {"body": texto}}}
                c.post(FCM_URL, json=carga).raise_for_status()
            return True
        except httpx.HTTPError:
            return False  # no revierte la asignación: el aviso queda PENDIENTE en la bandeja

    def despachar(notificacion_id):
        with Sesion() as s, s.begin():
            n = s.get(NotificacionPendiente, notificacion_id, with_for_update=True)
            if n is None or n.estado != "PENDIENTE":
                return
            n.intentos += 1
            if notificar(s.get(Billetera, n.tecnico_id).fcm_token, n.texto):
                n.estado = "ENVIADA"

    def reintentar_pendientes():
        """Reenvía los avisos PENDIENTES; pensada para un trabajo programado. Devuelve cuántos reintentó."""
        with Sesion() as s:
            ids = [i for (i,) in s.query(NotificacionPendiente.id).filter_by(estado="PENDIENTE")]
        for i in ids:
            despachar(i)
        return len(ids)

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
            sol = s.get(Solicitud, solicitud_id, with_for_update=True)  # serializa asignaciones simultáneas
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
            aviso = NotificacionPendiente(
                clave=f"ASIG-{sol.id}",
                tecnico_id=bil.tecnico_id,
                texto="Fuiste asignado a una solicitud",
                creada_en=reloj(),
            )
            s.add(aviso)
            s.flush()
            aviso_id = aviso.id
        despachar(aviso_id)
        with Sesion() as s:
            return _respuesta(s.get(Solicitud, solicitud_id), s.get(Billetera, pos.tecnico_id), comision)

    def _respuesta(sol, bil, comision):
        return {
            "solicitudId": sol.id,
            "estado": "E03",
            "comisionRetenidaCop": comision,
            "contacto": {"nombre": bil.nombre, "telefono": bil.telefono},
        }

    app.state.reintentar_pendientes = reintentar_pendientes
    return app


def _aware(d):
    return d if d.tzinfo else d.replace(tzinfo=UTC)
