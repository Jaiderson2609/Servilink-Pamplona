package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.dominio.Solicitud;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Anuncia que algo cambió, sin datos: quien escucha vuelve a pedir lo que necesita por la API, que es la que autoriza.
 * Los eventos se entregan después del COMMIT (ver infraestructura.EntregaEventos): una transacción revertida no avisa.
 */
@Component
public class PublicadorEventos {

    /** La solicitud cambió de estado, recibió una postulación o se canceló o expiró. */
    public record SolicitudCambio(Long solicitudId, Long hogarId, Long tecnicoId) {}

    /** Hay un mensaje nuevo en el chat de la solicitud. */
    public record MensajeNuevo(Long solicitudId, Long hogarId, Long tecnicoId) {}

    private final ApplicationEventPublisher publicador;

    public PublicadorEventos(ApplicationEventPublisher publicador) {
        this.publicador = publicador;
    }

    public void solicitud(Solicitud s) {
        publicador.publishEvent(new SolicitudCambio(s.getId(), s.getHogarId(), s.getTecnicoId()));
    }

    public void mensaje(Solicitud s) {
        publicador.publishEvent(new MensajeNuevo(s.getId(), s.getHogarId(), s.getTecnicoId()));
    }
}
