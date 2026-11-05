package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.dominio.*;
import co.edu.unipamplona.servilink.repositorio.*;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ejecución del servicio: llegada, cierre, confirmación y cancelación (RF-027 a RF-031, RF-041). */
@Service
public class EjecucionService {

    private final SolicitudRepositorio solicitudes;
    private final BilleteraRepositorio billeteras;
    private final MovimientoRepositorio libro;
    private final NotificacionRepositorio avisos;
    private final SolicitudService consultas;
    private final Clock reloj;

    public EjecucionService(SolicitudRepositorio solicitudes, BilleteraRepositorio billeteras,
                            MovimientoRepositorio libro, NotificacionRepositorio avisos, SolicitudService consultas,
                            Clock reloj) {
        this.solicitudes = solicitudes;
        this.billeteras = billeteras;
        this.libro = libro;
        this.avisos = avisos;
        this.consultas = consultas;
        this.reloj = reloj;
    }

    /** TR-04 (RF-029): solo el técnico asignado marca su llegada. */
    @Transactional
    public Efecto<SolicitudService.Vista> llegada(Long tecnicoId, Long solicitudId) {
        Solicitud s = solicitudes.bloquear(solicitudId).orElseThrow(SolicitudService::noEncontrada);
        exigirTecnico(s, tecnicoId);
        if (!"E03".equals(s.getEstado())) {
            throw estadoInvalido(s);
        }
        s.setEstado("E04");
        Long aviso = aviso("LLEG-" + s.getId(), s.getHogarId(), "El técnico llegó a tu domicilio");
        return new Efecto<>(consultas.vista(s, tecnicoId, "TECNICO"), aviso);
    }

    /** RF-031: el técnico marca el trabajo como terminado; queda a la espera de la confirmación del hogar. */
    @Transactional
    public Efecto<SolicitudService.Vista> trabajoTerminado(Long tecnicoId, Long solicitudId) {
        Solicitud s = solicitudes.bloquear(solicitudId).orElseThrow(SolicitudService::noEncontrada);
        exigirTecnico(s, tecnicoId);
        if (!"E04".equals(s.getEstado())) {
            throw estadoInvalido(s);
        }
        if (s.getTerminadoEn() != null) {
            return Efecto.sinAviso(consultas.vista(s, tecnicoId, "TECNICO")); // reintento idempotente
        }
        s.setTerminadoEn(reloj.instant());
        Long aviso = aviso("TERM-" + s.getId(), s.getHogarId(), "El técnico terminó el trabajo: confírmalo");
        return new Efecto<>(consultas.vista(s, tecnicoId, "TECNICO"), aviso);
    }

    /** TR-05 (RF-031 y RF-041): al confirmar, la retención se convierte en comisión descontada. */
    @Transactional
    public Efecto<SolicitudService.Vista> confirmar(Long hogarId, Long solicitudId) {
        Solicitud s = solicitudes.bloquear(solicitudId).orElseThrow(SolicitudService::noEncontrada);
        exigirHogar(s, hogarId);
        if ("E05".equals(s.getEstado())) {
            return Efecto.sinAviso(consultas.vista(s, hogarId, "HOGAR")); // reintento idempotente
        }
        if (!"E04".equals(s.getEstado())) {
            throw estadoInvalido(s);
        }
        if (s.getTerminadoEn() == null) {
            throw new ErrorNegocio(HttpStatus.CONFLICT, "Trabajo no terminado", "El técnico aún no lo marcó terminado");
        }
        Billetera b = billeteras.bloquear(s.getTecnicoId()).orElseThrow();
        long comision = s.getComisionCop();
        b.cobrar(comision);
        libro.save(new Movimiento(b.getTecnicoId(), s.getId(), "COMISION", comision, b.getDisponible(),
                "COM-" + s.getId(), reloj.instant()));
        s.setEstado("E05");
        Long aviso = aviso("FIN-" + s.getId(), s.getTecnicoId(), "El hogar confirmó el trabajo. ¡Gracias!");
        return new Efecto<>(consultas.vista(s, hogarId, "HOGAR"), aviso);
    }

    /** RF-027 y RF-028: libre en E01 y E02; desde E03 exige motivo y libera la comisión (RN-15). */
    @Transactional
    public Efecto<SolicitudService.Vista> cancelar(Long usuarioId, String rol, Long solicitudId, String motivo) {
        Solicitud s = solicitudes.bloquear(solicitudId).orElseThrow(SolicitudService::noEncontrada);
        String estado = s.getEstado();
        Long avisoId = null;
        if ("E01".equals(estado) || "E02".equals(estado)) {
            if (!"HOGAR".equals(rol)) {
                throw new ErrorNegocio(HttpStatus.FORBIDDEN, "Sin permiso", "Solo el hogar cancela antes de asignar");
            }
            exigirHogar(s, usuarioId);
        } else if ("E03".equals(estado) || "E04".equals(estado)) {
            if ("HOGAR".equals(rol)) {
                exigirHogar(s, usuarioId);
            } else {
                exigirTecnico(s, usuarioId);
            }
            if (motivo == null || motivo.isBlank() || motivo.trim().length() < 5) {
                throw new ErrorNegocio(HttpStatus.BAD_REQUEST, "Solicitud invalida",
                        "Cancelar un servicio asignado exige un motivo de al menos 5 caracteres");
            }
            Billetera b = billeteras.bloquear(s.getTecnicoId()).orElseThrow();
            long comision = s.getComisionCop();
            b.liberar(comision);
            libro.save(new Movimiento(b.getTecnicoId(), s.getId(), "LIBERACION", comision, b.getDisponible(),
                    "LIB-" + s.getId(), reloj.instant()));
            Long contraparte = "HOGAR".equals(rol) ? s.getTecnicoId() : s.getHogarId();
            avisoId = aviso("CANC-" + s.getId(), contraparte, "El servicio fue cancelado");
        } else {
            throw estadoInvalido(s);
        }
        s.setEstado("E07");
        s.setCanceladaPor(rol);
        s.setMotivoCancelacion(motivo == null || motivo.isBlank() ? null : motivo.trim());
        return new Efecto<>(consultas.vista(s, usuarioId, rol), avisoId);
    }

    /**
     * Cancelación por un administrador (disputas). Cubre E01 a E04; desde E03 libera la comisión retenida (RN-15).
     * Avisa al hogar de inmediato y deja el aviso del técnico en la bandeja de reintentos.
     */
    @Transactional
    public Efecto<Solicitud> cancelarAdministrativa(Long solicitudId, String motivo) {
        Solicitud s = solicitudes.bloquear(solicitudId).orElseThrow(SolicitudService::noEncontrada);
        if (!java.util.List.of("E01", "E02", "E03", "E04").contains(s.getEstado())) {
            throw estadoInvalido(s);
        }
        if (motivo == null || motivo.trim().length() < 5) {
            throw new ErrorNegocio(HttpStatus.BAD_REQUEST, "Solicitud invalida", "El motivo debe tener al menos 5 caracteres");
        }
        boolean asignada = s.getTecnicoId() != null && java.util.List.of("E03", "E04").contains(s.getEstado());
        if (asignada) {
            Billetera b = billeteras.bloquear(s.getTecnicoId()).orElseThrow();
            long comision = s.getComisionCop();
            b.liberar(comision);
            libro.save(new Movimiento(b.getTecnicoId(), s.getId(), "LIBERACION", comision, b.getDisponible(),
                    "LIB-" + s.getId(), reloj.instant()));
            aviso("CANCA-" + s.getId() + "-T", s.getTecnicoId(), "Un administrador canceló el servicio");
        }
        Long avisoHogar = aviso("CANCA-" + s.getId() + "-H", s.getHogarId(), "Un administrador canceló tu solicitud");
        s.setEstado("E07");
        s.setCanceladaPor("ADMIN");
        s.setMotivoCancelacion(motivo.trim());
        return new Efecto<>(s, avisoHogar);
    }

    private Long aviso(String clave, Long destinatario, String texto) {
        Instant ahora = reloj.instant();
        return avisos.save(new NotificacionPendiente(clave, destinatario, texto, ahora)).getId();
    }

    private static void exigirTecnico(Solicitud s, Long tecnicoId) {
        if (!tecnicoId.equals(s.getTecnicoId())) {
            throw new ErrorNegocio(HttpStatus.FORBIDDEN, "Sin permiso", "No eres el técnico asignado");
        }
    }

    private static void exigirHogar(Solicitud s, Long hogarId) {
        if (!s.getHogarId().equals(hogarId)) {
            throw new ErrorNegocio(HttpStatus.FORBIDDEN, "Sin permiso", "La solicitud es de otro hogar");
        }
    }

    private static ErrorNegocio estadoInvalido(Solicitud s) {
        return new ErrorNegocio(HttpStatus.CONFLICT, "Estado invalido", "La solicitud está en " + s.getEstado());
    }
}
