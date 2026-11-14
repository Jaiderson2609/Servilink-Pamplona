package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.dominio.*;
import co.edu.unipamplona.servilink.repositorio.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Negociación del precio (RF-023): el técnico hace hasta 2 ofertas (inicial y final) y el hogar 1 contraoferta.
 * El precio vigente de la postulación solo cambia cuando el técnico acepta la contraoferta o hace su oferta final.
 */
@Service
public class NegociacionService {

    public static final int MAX_OFERTAS_TECNICO = 2;
    public static final int MAX_CONTRAOFERTAS_HOGAR = 1;

    private final SolicitudRepositorio solicitudes;
    private final PostulacionRepositorio postulaciones;
    private final OfertaRepositorio ofertas;
    private final Clock reloj;
    private final PublicadorEventos eventos;

    public NegociacionService(SolicitudRepositorio solicitudes, PostulacionRepositorio postulaciones,
                              OfertaRepositorio ofertas, Clock reloj, PublicadorEventos eventos) {
        this.solicitudes = solicitudes;
        this.postulaciones = postulaciones;
        this.ofertas = ofertas;
        this.reloj = reloj;
        this.eventos = eventos;
    }

    /** Nueva oferta: contraoferta del hogar u oferta final del técnico. */
    @Transactional
    public SolicitudService.VistaOferta ofertar(Long usuarioId, String rol, Long postulacionId, int monto) {
        Postulacion pos = postulacion(postulacionId);
        Solicitud sol = bloquear(pos);
        boolean hogar = "HOGAR".equals(rol);
        if (hogar ? !sol.getHogarId().equals(usuarioId) : !pos.getTecnicoId().equals(usuarioId)) {
            throw new ErrorNegocio(Codigo.FORBIDDEN, "No participas en esta postulación");
        }
        exigirNegociable(sol, pos);
        List<Oferta> historial = ofertas.findByPostulacionIdOrderBySecuencia(pos.getId());
        String autor = hogar ? Oferta.HOGAR : Oferta.TECNICO;
        long propias = historial.stream().filter(o -> o.getAutor().equals(autor)).count();
        if (propias >= (hogar ? MAX_CONTRAOFERTAS_HOGAR : MAX_OFERTAS_TECNICO)) {
            throw new ErrorNegocio(Codigo.NEGOTIATION_LIMIT_REACHED, hogar
                    ? "Solo puedes enviar una contraoferta por postulación"
                    : "Ya usaste tus dos ofertas en esta postulación");
        }
        Oferta ultima = historial.get(historial.size() - 1);
        if (!hogar && !(Oferta.HOGAR.equals(ultima.getAutor()) && "PENDIENTE".equals(ultima.getEstado()))) {
            throw new ErrorNegocio(Codigo.INVALID_STATE_TRANSITION, "No hay una contraoferta que responder");
        }
        Oferta nueva = ofertas.save(new Oferta(pos.getId(), autor, monto, ultima.getSecuencia() + 1, reloj.instant()));
        if (!hogar) { // la oferta final reemplaza la contraoferta y fija el precio vigente
            ultima.setEstado("RECHAZADA");
            pos.setPrecio(monto);
        }
        eventos.solicitud(sol);
        return vista(nueva);
    }

    /** El técnico acepta la contraoferta del hogar: ese monto pasa a ser el precio vigente. */
    @Transactional
    public SolicitudService.VistaOferta aceptar(Long tecnicoId, Long ofertaId) {
        Oferta oferta = ofertas.findById(ofertaId)
                .orElseThrow(() -> new ErrorNegocio(Codigo.NOT_FOUND, "Oferta inexistente"));
        Postulacion pos = postulacion(oferta.getPostulacionId());
        Solicitud sol = bloquear(pos);
        if (!pos.getTecnicoId().equals(tecnicoId)) {
            throw new ErrorNegocio(Codigo.FORBIDDEN, "La oferta no es de una postulación tuya");
        }
        if (!Oferta.HOGAR.equals(oferta.getAutor())) {
            throw new ErrorNegocio(Codigo.INVALID_STATE_TRANSITION, "Solo se aceptan contraofertas del hogar");
        }
        if ("ACEPTADA".equals(oferta.getEstado())) {
            return vista(oferta); // reintento idempotente
        }
        if (!"PENDIENTE".equals(oferta.getEstado())) {
            throw new ErrorNegocio(Codigo.INVALID_STATE_TRANSITION, "La contraoferta ya no está vigente");
        }
        exigirNegociable(sol, pos);
        oferta.setEstado("ACEPTADA");
        pos.setPrecio(oferta.getMonto());
        eventos.solicitud(sol);
        return vista(oferta);
    }

    /** El técnico retira su postulación mientras no haya sido elegido. */
    @Transactional
    public String retirar(Long tecnicoId, Long postulacionId) {
        Postulacion pos = postulacion(postulacionId);
        Solicitud sol = bloquear(pos);
        if (!pos.getTecnicoId().equals(tecnicoId)) {
            throw new ErrorNegocio(Codigo.FORBIDDEN, "La postulación es de otro técnico");
        }
        if ("RETIRADA".equals(pos.getEstado())) {
            return pos.getEstado(); // reintento idempotente
        }
        if (!"ACTIVA".equals(pos.getEstado())
                || !EstadoSolicitud.esUnoDe(sol.getEstado(), EstadoSolicitud.E01, EstadoSolicitud.E02)) {
            throw new ErrorNegocio(Codigo.INVALID_STATE_TRANSITION, "La postulación ya no se puede retirar");
        }
        pos.setEstado("RETIRADA");
        eventos.solicitud(sol);
        return pos.getEstado();
    }

    private void exigirNegociable(Solicitud sol, Postulacion pos) {
        if (!EstadoSolicitud.E02.es(sol.getEstado()) || !"ACTIVA".equals(pos.getEstado())) {
            throw new ErrorNegocio(Codigo.INVALID_STATE_TRANSITION, "La postulación no admite negociación");
        }
        Instant ahora = reloj.instant();
        if (!ahora.isBefore(sol.getExpiraEn())) { // RN-02: decide el reloj del servidor
            throw new ErrorNegocio(Codigo.SERVICE_EXPIRED, "La ventana de selección terminó");
        }
    }

    private Postulacion postulacion(Long id) {
        return postulaciones.findById(id).orElseThrow(() -> new ErrorNegocio(Codigo.NOT_FOUND, "Postulación inexistente"));
    }

    private Solicitud bloquear(Postulacion pos) {
        return solicitudes.bloquear(pos.getSolicitudId())
                .orElseThrow(() -> new ErrorNegocio(Codigo.NOT_FOUND, "Solicitud inexistente"));
    }

    private static SolicitudService.VistaOferta vista(Oferta o) {
        return new SolicitudService.VistaOferta(o.getId(), o.getAutor(), o.getMonto(), o.getSecuencia(), o.getEstado(), o.getCreadaEn());
    }
}
