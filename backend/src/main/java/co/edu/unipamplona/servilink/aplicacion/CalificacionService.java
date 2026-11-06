package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.dominio.Calificacion;
import co.edu.unipamplona.servilink.dominio.Solicitud;
import co.edu.unipamplona.servilink.repositorio.CalificacionRepositorio;
import co.edu.unipamplona.servilink.repositorio.SolicitudRepositorio;
import co.edu.unipamplona.servilink.repositorio.UsuarioRepositorio;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calificaciones mutuas (RF-043): solo con el servicio finalizado (E05) y una vez por parte. */
@Service
public class CalificacionService {

    private final SolicitudRepositorio solicitudes;
    private final CalificacionRepositorio calificaciones;
    private final UsuarioRepositorio usuarios;
    private final Clock reloj;

    public CalificacionService(SolicitudRepositorio solicitudes, CalificacionRepositorio calificaciones,
                               UsuarioRepositorio usuarios, Clock reloj) {
        this.solicitudes = solicitudes;
        this.calificaciones = calificaciones;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    public record Vista(Long id, Long solicitudId, Long destinatarioId, Integer estrellas, String comentario,
                        Instant creadaEn) {}

    public record Reputacion(Long tecnicoId, Double promedio, long calificaciones) {}

    @Transactional
    public Vista calificar(Long usuarioId, Long solicitudId, int estrellas, String comentario) {
        Solicitud s = solicitudes.bloquear(solicitudId).orElseThrow(SolicitudService::noEncontrada);
        Participacion.exigirParte(s, usuarioId);
        if (!"E05".equals(s.getEstado())) {
            throw new ErrorNegocio(Codigo.INVALID_STATE_TRANSITION, "Solo se califica un servicio finalizado");
        }
        if (calificaciones.existsBySolicitudIdAndAutorId(solicitudId, usuarioId)) {
            throw new ErrorNegocio(Codigo.DUPLICATE_RATING, "Ya calificaste este servicio");
        }
        String texto = comentario == null || comentario.isBlank() ? null : comentario.strip();
        Calificacion c = calificaciones.save(new Calificacion(solicitudId, usuarioId,
                Participacion.otraParte(s, usuarioId), estrellas, texto, reloj.instant()));
        return new Vista(c.getId(), c.getSolicitudId(), c.getDestinatarioId(), c.getEstrellas(), c.getComentario(),
                c.getCreadaEn());
    }

    @Transactional(readOnly = true)
    public Reputacion reputacion(Long tecnicoId) {
        boolean existe = usuarios.findById(tecnicoId).map(u -> "TECNICO".equals(u.getRol())).orElse(false);
        if (!existe) {
            throw new ErrorNegocio(Codigo.NOT_FOUND, "Técnico inexistente");
        }
        return resumen(tecnicoId);
    }

    /** Promedio con dos decimales; sin calificaciones el promedio es nulo, no cero. */
    public Reputacion resumen(Long usuarioId) {
        long total = calificaciones.countByDestinatarioId(usuarioId);
        Double prom = total == 0 ? null : Math.round(calificaciones.promedio(usuarioId) * 100.0) / 100.0;
        return new Reputacion(usuarioId, prom, total);
    }
}
