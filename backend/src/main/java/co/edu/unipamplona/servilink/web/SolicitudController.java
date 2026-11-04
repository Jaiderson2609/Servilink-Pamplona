package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.AsignacionService;
import co.edu.unipamplona.servilink.aplicacion.NotificacionService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/solicitudes")
public class SolicitudController {

    private final AsignacionService asignacion;
    private final NotificacionService notificaciones;

    public SolicitudController(AsignacionService asignacion, NotificacionService notificaciones) {
        this.asignacion = asignacion;
        this.notificaciones = notificaciones;
    }

    @PostMapping("/{solicitudId}/asignacion")
    public ResponseEntity<Map<String, Object>> asignar(@PathVariable Long solicitudId,
                                                       @Valid @RequestBody AsignacionCrear cuerpo,
                                                       @AuthenticationPrincipal Jwt jwt) {
        var r = asignacion.asignar(Long.valueOf(jwt.getSubject()), solicitudId, cuerpo.postulacionId());
        if (r.avisoId() != null) {
            notificaciones.despachar(r.avisoId()); // después del COMMIT y fuera de la transacción de la asignación
        }
        return ResponseEntity.ok(Map.of(
                "solicitudId", r.solicitudId(),
                "estado", "E03",
                "comisionRetenidaCop", r.comisionCop(),
                "contacto", Map.of("nombre", r.nombreTecnico(), "telefono", r.telefonoTecnico())));
    }
}
