package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.AdministracionService;
import co.edu.unipamplona.servilink.aplicacion.NotificacionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Todo lo que cuelga de /api/v1/admin exige el rol ADMIN (ver SeguridadConfig). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdministracionService admin;
    private final NotificacionService notificaciones;

    public AdminController(AdministracionService admin, NotificacionService notificaciones) {
        this.admin = admin;
        this.notificaciones = notificaciones;
    }

    private static Long id(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }

    @GetMapping("/resumen")
    public Map<String, Object> resumen() {
        return admin.resumen();
    }

    @GetMapping("/usuarios")
    public AdministracionService.Pagina<AdministracionService.UsuarioAdmin> usuarios(
            @RequestParam(required = false) String rol, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return admin.usuarios(rol, q, pagina, tamano);
    }

    @PostMapping("/usuarios/{usuarioId}/suspension")
    public AdministracionService.UsuarioAdmin suspender(@PathVariable Long usuarioId,
            @Valid @RequestBody Peticiones.MotivoAdmin m, @AuthenticationPrincipal Jwt jwt) {
        return admin.suspender(id(jwt), usuarioId, m.motivo());
    }

    @DeleteMapping("/usuarios/{usuarioId}/suspension")
    public AdministracionService.UsuarioAdmin reactivar(@PathVariable Long usuarioId, @AuthenticationPrincipal Jwt jwt) {
        return admin.reactivar(id(jwt), usuarioId);
    }

    @GetMapping("/solicitudes")
    public AdministracionService.Pagina<AdministracionService.SolicitudAdmin> solicitudes(
            @RequestParam(required = false) String estado, @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return admin.solicitudes(estado, pagina, tamano);
    }

    @PostMapping("/solicitudes/{solicitudId}/cancelacion")
    public AdministracionService.SolicitudAdmin cancelar(@PathVariable Long solicitudId,
            @Valid @RequestBody Peticiones.MotivoAdmin m, @AuthenticationPrincipal Jwt jwt) {
        var e = admin.cancelarSolicitud(id(jwt), solicitudId, m.motivo());
        if (e.avisoId() != null) {
            notificaciones.despachar(e.avisoId()); // después del COMMIT, fuera de la transacción
        }
        return e.valor();
    }

    @GetMapping("/libro")
    public List<AdministracionService.MovimientoAdmin> libro(@RequestParam Long solicitudId) {
        return admin.libro(solicitudId);
    }

    @GetMapping("/auditoria")
    public AdministracionService.Pagina<AdministracionService.Auditoria> auditoria(
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return admin.auditoria(pagina, tamano);
    }
}
