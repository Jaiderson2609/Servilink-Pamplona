package co.edu.unipamplona.servilink.infraestructura;

import co.edu.unipamplona.servilink.aplicacion.PublicadorEventos.MensajeNuevo;
import co.edu.unipamplona.servilink.aplicacion.PublicadorEventos.SolicitudCambio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Entrega los eventos a los sockets solo después del COMMIT (fase por defecto de TransactionalEventListener):
 * una transacción revertida no deja avisos fantasma. Un fallo al avisar nunca afecta a la operación ya confirmada.
 */
@Component
public class EntregaEventos {

    private static final Logger LOG = LoggerFactory.getLogger(EntregaEventos.class);
    static final String ABIERTAS = "{\"tipo\":\"abiertas\"}";

    private final ManejadorEventos canal;

    public EntregaEventos(ManejadorEventos canal) {
        this.canal = canal;
    }

    @TransactionalEventListener(fallbackExecution = true)
    void alCambiarSolicitud(SolicitudCambio e) {
        try {
            String aviso = "{\"tipo\":\"solicitud\",\"id\":" + e.solicitudId() + "}";
            canal.avisarUsuario(e.hogarId(), aviso);
            canal.avisarUsuario(e.tecnicoId(), aviso);
            canal.avisarRol("TECNICO", ABIERTAS); // la lista de solicitudes abiertas pudo cambiar
        } catch (RuntimeException ex) {
            LOG.warn("No se pudo entregar el aviso de la solicitud {}: {}", e.solicitudId(), ex.toString());
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    void alLlegarMensaje(MensajeNuevo e) {
        try {
            String aviso = "{\"tipo\":\"chat\",\"id\":" + e.solicitudId() + "}";
            canal.avisarUsuario(e.hogarId(), aviso);
            canal.avisarUsuario(e.tecnicoId(), aviso);
        } catch (RuntimeException ex) {
            LOG.warn("No se pudo entregar el aviso del chat {}: {}", e.solicitudId(), ex.toString());
        }
    }
}
