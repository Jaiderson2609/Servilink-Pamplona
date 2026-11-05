package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.AsignacionService;
import co.edu.unipamplona.servilink.aplicacion.NotificacionService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Operaciones sobre una postulación concreta (DDS 9). */
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final AsignacionService asignacion;
    private final NotificacionService notificaciones;

    public ApplicationController(AsignacionService asignacion, NotificacionService notificaciones) {
        this.asignacion = asignacion;
        this.notificaciones = notificaciones;
    }

    /** El hogar elige al técnico (TR-03). Idempotente con Idempotency-Key. */
    @PatchMapping("/{applicationId}/select")
    public ResponseEntity<Map<String, Object>> elegir(@PathVariable Long applicationId, @AuthenticationPrincipal Jwt jwt) {
        Long hogarId = Long.valueOf(jwt.getSubject());
        var r = asignacion.asignar(hogarId, asignacion.solicitudDe(applicationId), applicationId);
        if (r.avisoId() != null) {
            notificaciones.despachar(r.avisoId());
        }
        return ResponseEntity.ok(Map.of(
                "solicitudId", r.solicitudId(),
                "estado", "E03",
                "comisionRetenidaCop", r.comisionCop(),
                "contacto", Map.of("nombre", r.nombreTecnico(), "telefono", r.telefonoTecnico())));
    }
}
