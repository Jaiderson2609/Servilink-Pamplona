package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.dominio.NotificacionPendiente;
import co.edu.unipamplona.servilink.infraestructura.Notificador;
import co.edu.unipamplona.servilink.repositorio.NotificacionRepositorio;
import co.edu.unipamplona.servilink.repositorio.PostulacionRepositorio;
import co.edu.unipamplona.servilink.repositorio.SolicitudRepositorio;
import co.edu.unipamplona.servilink.repositorio.UsuarioRepositorio;
import java.time.Clock;
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
    private final SolicitudRepositorio solicitudes;
    private final PostulacionRepositorio postulaciones;
    private final Clock reloj;

    public NotificacionService(NotificacionRepositorio avisos, UsuarioRepositorio usuarios, Notificador notificador,
                               TransactionTemplate tx,
                               @Value("${servilink.reintentos-habilitados}") boolean reintentosHabilitados,
                               SolicitudRepositorio solicitudes, PostulacionRepositorio postulaciones, Clock reloj) {
        this.avisos = avisos;
        this.usuarios = usuarios;
        this.notificador = notificador;
        this.tx = tx;
        this.reintentosHabilitados = reintentosHabilitados;
        this.solicitudes = solicitudes;
        this.postulaciones = postulaciones;
        this.reloj = reloj;
    }

    /** Guarda el token de aviso push del navegador de la persona; si otra cuenta lo tenía, se lo quita. */
    public void registrarToken(Long usuarioId, String token) {
        tx.executeWithoutResult(s -> {
            usuarios.liberarToken(token, usuarioId);
            usuarios.findById(usuarioId).ifPresent(u -> u.setTokenPush(token));
        });
    }

    /** Al salir de la sesión el dispositivo deja de recibir los avisos de esa cuenta. */
    public void quitarToken(Long usuarioId) {
        tx.executeWithoutResult(s -> usuarios.findById(usuarioId).ifPresent(u -> u.setTokenPush(null)));
    }

    /** Ejecuta el envío fuera de la petición: la persona que publica o postula no espera a Firebase. */
    public void enSegundoPlano(Runnable tarea) {
        Thread.ofVirtual().name("avisos-push").start(tarea);
    }

    /** Técnicos que pueden atender la solicitud (en su radio, verificados y disponibles). */
    public void avisarNuevaSolicitud(Long solicitudId) {
        try {
            var tecnicos = tx.execute(s -> usuarios.tecnicosParaAvisar(solicitudId));
            if (tecnicos != null) {
                tecnicos.forEach(t -> avisar("NUEVA-" + solicitudId + "-" + t, t, "Nueva solicitud cerca de ti"));
            }
        } catch (RuntimeException e) {
            LOG.warn("No se pudo avisar la solicitud {}: {}", solicitudId, e.toString());
        }
    }

    /** El hogar se entera de que un técnico se postuló a su solicitud. */
    public void avisarPostulacion(Long solicitudId, Long tecnicoId) {
        try {
            solicitudes.findById(solicitudId).ifPresent(sol ->
                    avisar("OFERTA-" + solicitudId + "-" + tecnicoId, sol.getHogarId(), "Recibiste una oferta"));
        } catch (RuntimeException e) {
            LOG.warn("No se pudo avisar la oferta de la solicitud {}: {}", solicitudId, e.toString());
        }
    }

    /** Contraoferta del hogar (avisa al técnico) u oferta final del técnico (avisa al hogar). */
    public void avisarOferta(Long postulacionId, boolean deHogar, Long ofertaId) {
        try {
            postulaciones.findById(postulacionId).ifPresent(pos -> {
                if (deHogar) {
                    avisar("CONTRA-" + ofertaId, pos.getTecnicoId(), "El hogar respondió con una contraoferta");
                } else {
                    solicitudes.findById(pos.getSolicitudId()).ifPresent(sol ->
                            avisar("FINAL-" + ofertaId, sol.getHogarId(), "El técnico envió su oferta final"));
                }
            });
        } catch (RuntimeException e) {
            LOG.warn("No se pudo avisar la oferta {}: {}", ofertaId, e.toString());
        }
    }

    /** Guarda el aviso en la bandeja y lo despacha; no hace nada si la persona no tiene el aviso push activo. */
    private void avisar(String clave, Long destinatarioId, String texto) {
        Long id = tx.execute(s -> {
            boolean conToken = usuarios.findById(destinatarioId)
                    .map(u -> u.getTokenPush() != null && !u.getTokenPush().isBlank()).orElse(false);
            if (!conToken || avisos.existsByClave(clave)) {
                return null;
            }
            return avisos.save(new NotificacionPendiente(clave, destinatarioId, texto, reloj.instant())).getId();
        });
        if (id != null) {
            despachar(id);
        }
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
