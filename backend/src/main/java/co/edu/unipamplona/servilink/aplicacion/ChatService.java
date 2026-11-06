package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.dominio.Mensaje;
import co.edu.unipamplona.servilink.dominio.Solicitud;
import co.edu.unipamplona.servilink.repositorio.MensajeRepositorio;
import co.edu.unipamplona.servilink.repositorio.SolicitudRepositorio;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Chat entre el hogar y el técnico asignado (RF-033): se escribe mientras el servicio está en curso; el historial se conserva. */
@Service
public class ChatService {

    public static final int MAX_POR_CONSULTA = 100;

    private final SolicitudRepositorio solicitudes;
    private final MensajeRepositorio mensajes;
    private final Clock reloj;

    public ChatService(SolicitudRepositorio solicitudes, MensajeRepositorio mensajes, Clock reloj) {
        this.solicitudes = solicitudes;
        this.mensajes = mensajes;
        this.reloj = reloj;
    }

    public record Vista(Long id, Long autorId, String texto, Instant creadoEn, boolean mio) {}

    @Transactional
    public Vista enviar(Long usuarioId, Long solicitudId, String texto) {
        Solicitud s = solicitudes.findById(solicitudId).orElseThrow(SolicitudService::noEncontrada);
        Participacion.exigirParte(s, usuarioId);
        if (!List.of("E03", "E04").contains(s.getEstado())) {
            throw new ErrorNegocio(Codigo.CHAT_CLOSED, "El chat solo está abierto mientras el servicio está en curso");
        }
        String limpio = texto == null ? "" : texto.strip();
        if (limpio.isEmpty() || limpio.length() > 500) {
            throw new ErrorNegocio(Codigo.VALIDATION_ERROR, "El mensaje debe tener entre 1 y 500 caracteres");
        }
        Mensaje m = mensajes.save(new Mensaje(solicitudId, usuarioId, limpio, reloj.instant()));
        return vista(m, usuarioId);
    }

    /** Consulta incremental: el cliente pide lo posterior al último mensaje que ya tiene. */
    @Transactional(readOnly = true)
    public List<Vista> listar(Long usuarioId, Long solicitudId, long despuesDe) {
        Solicitud s = solicitudes.findById(solicitudId).orElseThrow(SolicitudService::noEncontrada);
        Participacion.exigirParte(s, usuarioId);
        if (s.getTecnicoId() == null) {
            throw new ErrorNegocio(Codigo.CHAT_CLOSED, "El chat se abre cuando se asigna un técnico");
        }
        return mensajes.findBySolicitudIdAndIdGreaterThanOrderByIdAsc(solicitudId, despuesDe,
                PageRequest.of(0, MAX_POR_CONSULTA)).stream().map(m -> vista(m, usuarioId)).toList();
    }

    private static Vista vista(Mensaje m, Long usuarioId) {
        return new Vista(m.getId(), m.getAutorId(), m.getTexto(), m.getCreadoEn(), m.getAutorId().equals(usuarioId));
    }
}
