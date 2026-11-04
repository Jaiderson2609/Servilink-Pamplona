package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.dominio.NotificacionPendiente;
import co.edu.unipamplona.servilink.infraestructura.Notificador;
import co.edu.unipamplona.servilink.repositorio.NotificacionRepositorio;
import co.edu.unipamplona.servilink.repositorio.UsuarioRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Envío de avisos con la bandeja de salida: entrega "al menos una vez" y reintentos programados. */
@Service
public class NotificacionService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificacionService.class);

    private final NotificacionRepositorio avisos;
    private final UsuarioRepositorio usuarios;
    private final Notificador notificador;
    private final TransactionTemplate tx;
    private final boolean reintentosHabilitados;

    public NotificacionService(NotificacionRepositorio avisos, UsuarioRepositorio usuarios, Notificador notificador,
                               TransactionTemplate tx,
                               @Value("${servilink.reintentos-habilitados}") boolean reintentosHabilitados) {
        this.avisos = avisos;
        this.usuarios = usuarios;
        this.notificador = notificador;
        this.tx = tx;
        this.reintentosHabilitados = reintentosHabilitados;
    }

    /** Intenta entregar un aviso pendiente. Un fallo de red nunca se propaga a quien lo invocó. */
    public void despachar(Long avisoId) {
        try {
            tx.executeWithoutResult(s -> {
                NotificacionPendiente n = avisos.bloquear(avisoId).orElse(null);
                if (n == null || !"PENDIENTE".equals(n.getEstado())) {
                    return;
                }
                String token = usuarios.findById(n.getDestinatarioId()).map(u -> u.getTokenPush()).orElse(null);
                n.registrarIntento(notificador.enviar(token, n.getTexto()));
            });
        } catch (RuntimeException e) {
            LOG.warn("No se pudo despachar el aviso {}: {}", avisoId, e.toString());
        }
    }

    /** DEF-C: un trabajo programado reenvía los avisos que quedaron PENDIENTES. */
    @Scheduled(fixedDelayString = "${servilink.reintentos-cada-ms}", initialDelayString = "${servilink.reintentos-cada-ms}")
    void reintentarProgramado() {
        if (reintentosHabilitados) {
            reintentarPendientes();
        }
    }

    /** @return cuántos avisos se intentó reenviar. */
    public int reintentarPendientes() {
        var ids = avisos.idsPendientes();
        ids.forEach(this::despachar);
        return ids.size();
    }
}
