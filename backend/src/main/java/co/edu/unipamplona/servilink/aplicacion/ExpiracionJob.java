package co.edu.unipamplona.servilink.aplicacion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RF-026: cada cierto tiempo pasa a E06 las solicitudes cuya ventana venció. */
@Component
public class ExpiracionJob {

    private final SolicitudService solicitudes;
    private final boolean habilitado;

    public ExpiracionJob(SolicitudService solicitudes,
                         @Value("${servilink.reintentos-habilitados}") boolean habilitado) {
        this.solicitudes = solicitudes;
        this.habilitado = habilitado;
    }

    @Scheduled(fixedDelayString = "${servilink.reintentos-cada-ms}", initialDelayString = "${servilink.reintentos-cada-ms}")
    void barrer() {
        if (habilitado) {
            solicitudes.expirarVencidas();
        }
    }
}
