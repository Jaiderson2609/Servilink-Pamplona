package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/service-requests")
public class SolicitudController {

    private final SolicitudService solicitudes;
    private final EjecucionService ejecucion;
    private final NotificacionService notificaciones;

    public SolicitudController(SolicitudService solicitudes, EjecucionService ejecucion,
                               NotificacionService notificaciones) {
        this.solicitudes = solicitudes;
        this.ejecucion = ejecucion;
        this.notificaciones = notificaciones;
    }

    private static Long id(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private static String rol(Jwt jwt) { return jwt.getClaimAsString("rol"); }

    /** Despacha el aviso después del COMMIT y fuera de la transacción de la operación. */
    private <T> T despachar(Efecto<T> e) {
        if (e.avisoId() != null) {
            notificaciones.despachar(e.avisoId());
        }
        return e.valor();
    }

    @PostMapping
    public ResponseEntity<SolicitudService.Vista> crear(@Valid @RequestBody Peticiones.SolicitudCrear c,
                                                        @AuthenticationPrincipal Jwt jwt) {
        var v = solicitudes.crear(id(jwt), c.oficioId(), c.descripcion(), c.ubicacion().lat(), c.ubicacion().lng());
        return ResponseEntity.created(URI.create("/api/v1/service-requests/" + v.id())).body(v);
    }

    @GetMapping("/mine")
    public List<SolicitudService.Vista> mias(@AuthenticationPrincipal Jwt jwt) {
        return solicitudes.mias(id(jwt), rol(jwt));
    }

    @GetMapping("/open")
    public List<SolicitudService.Vista> abiertas(@AuthenticationPrincipal Jwt jwt) {
        return solicitudes.abiertas(id(jwt));
    }

    @GetMapping("/{solicitudId}")
    public SolicitudService.Vista obtener(@PathVariable Long solicitudId, @AuthenticationPrincipal Jwt jwt) {
        return solicitudes.obtener(id(jwt), rol(jwt), solicitudId);
    }

    @PostMapping("/{solicitudId}/applications")
    public ResponseEntity<SolicitudService.VistaPostulacion> postular(@PathVariable Long solicitudId,
            @Valid @RequestBody Peticiones.PostulacionCrear p, @AuthenticationPrincipal Jwt jwt) {
        var v = solicitudes.postular(id(jwt), solicitudId, p.precioCop(), p.mensaje());
        return ResponseEntity.created(URI.create("/api/v1/applications/" + v.id()))
                .body(v);
    }

    @GetMapping("/{solicitudId}/applications")
    public List<SolicitudService.VistaPostulacion> postulaciones(@PathVariable Long solicitudId,
                                                                 @AuthenticationPrincipal Jwt jwt) {
        return solicitudes.postulaciones(id(jwt), solicitudId);
    }

    @PatchMapping("/{solicitudId}/arrive")
    public SolicitudService.Vista llegada(@PathVariable Long solicitudId, @AuthenticationPrincipal Jwt jwt) {
        return despachar(ejecucion.llegada(id(jwt), solicitudId));
    }

    @PatchMapping("/{solicitudId}/complete")
    public SolicitudService.Vista terminado(@PathVariable Long solicitudId, @AuthenticationPrincipal Jwt jwt) {
        return despachar(ejecucion.trabajoTerminado(id(jwt), solicitudId));
    }

    @PatchMapping("/{solicitudId}/confirm")
    public SolicitudService.Vista confirmar(@PathVariable Long solicitudId, @AuthenticationPrincipal Jwt jwt) {
        return despachar(ejecucion.confirmar(id(jwt), solicitudId));
    }

    @PatchMapping("/{solicitudId}/cancel")
    public SolicitudService.Vista cancelar(@PathVariable Long solicitudId,
            @Valid @RequestBody(required = false) Peticiones.Cancelacion c, @AuthenticationPrincipal Jwt jwt) {
        return despachar(ejecucion.cancelar(id(jwt), rol(jwt), solicitudId, c == null ? null : c.motivo()));
    }
}
