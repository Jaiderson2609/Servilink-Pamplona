package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.dominio.EstadoSolicitud;
import co.edu.unipamplona.servilink.aplicacion.AsignacionService;
import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import co.edu.unipamplona.servilink.aplicacion.NotificacionService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Operaciones sobre una postulación concreta (DDS 9). */
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationController.class);

    private final AsignacionService asignacion;
    private final NotificacionService notificaciones;
    private final ObservationRegistry observaciones;

    public ApplicationController(AsignacionService asignacion, NotificacionService notificaciones,
                                 ObservationRegistry observaciones) {
        this.asignacion = asignacion;
        this.notificaciones = notificaciones;
        this.observaciones = observaciones;
    }

    /**
     * El hogar elige al técnico (TR-03). Idempotente con Idempotency-Key.
     *
     * Es el endpoint crítico del negocio: mueve dinero (retiene la comisión) y cambia el estado de E02 a E03.
     * Por eso está observado: una observación produce a la vez la métrica `servilink_asignacion_seconds`
     * (tasa, latencia y errores por resultado) y un span `servilink.asignacion` dentro de la traza de la petición.
     * El registro y las etiquetas solo llevan identificadores internos y el código de resultado: nunca el nombre,
     * el teléfono, el correo ni la ubicación de nadie (Ley 1581 de 2012).
     */
    @PatchMapping("/{applicationId}/select")
    public ResponseEntity<Map<String, Object>> elegir(@PathVariable Long applicationId, @AuthenticationPrincipal Jwt jwt) {
        Long hogarId = Long.valueOf(jwt.getSubject());
        Observation obs = Observation.start("servilink.asignacion", observaciones);
        try (Observation.Scope ignorado = obs.openScope()) {
            Long solicitudId = asignacion.solicitudDe(applicationId);
            var r = asignacion.asignar(hogarId, solicitudId, applicationId);
            if (r.avisoId() != null) {
                notificaciones.despachar(r.avisoId());
            }
            obs.lowCardinalityKeyValue("resultado", r.avisoId() != null ? "ASIGNADA" : "REINTENTO_IDEMPOTENTE");
            LOG.atInfo().addKeyValue("evento", "asignacion").addKeyValue("solicitudId", solicitudId)
                    .addKeyValue("resultado", r.avisoId() != null ? "ASIGNADA" : "REINTENTO_IDEMPOTENTE")
                    .log("Asignación procesada");
            return ResponseEntity.ok(Map.of(
                    "solicitudId", r.solicitudId(),
                    "estado", EstadoSolicitud.E03.codigo(),
                    "comisionRetenidaCop", r.comisionCop(),
                    "contacto", Map.of("nombre", r.nombreTecnico(), "telefono", r.telefonoTecnico())));
        } catch (ErrorNegocio e) {
            // Rechazo de negocio esperado (sin saldo, ventana vencida...): cuenta como resultado, no como fallo del sistema.
            obs.lowCardinalityKeyValue("resultado", e.codigo().name());
            LOG.atWarn().addKeyValue("evento", "asignacion").addKeyValue("resultado", e.codigo().name())
                    .log("Asignación rechazada por regla de negocio");
            throw e;
        } catch (RuntimeException e) {
            obs.lowCardinalityKeyValue("resultado", "ERROR_INTERNO");
            obs.error(e);
            throw e;
        } finally {
            obs.stop();
        }
    }
}
