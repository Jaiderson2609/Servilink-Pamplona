package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.AdministracionService;
import co.edu.unipamplona.servilink.aplicacion.Codigo;
import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import co.edu.unipamplona.servilink.aplicacion.NotificacionService;
import co.edu.unipamplona.servilink.aplicacion.VerificacionService;
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
    private final VerificacionService verificacion;

    public AdminController(AdministracionService admin, NotificacionService notificaciones, VerificacionService verificacion) {
        this.admin = admin;
        this.notificaciones = notificaciones;
        this.verificacion = verificacion;
    }

    /** Técnicos por estado de verificación (por defecto, los que esperan revisión). */
    @GetMapping("/technicians")
    public AdministracionService.Pagina<VerificacionService.Pendiente> tecnicos(
            @RequestParam(required = false) String estado, @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return verificacion.listar(estado, pagina, tamano);
    }

    /** Documento privado de un técnico en revisión: nunca se guarda en caché ni se interpreta como otro tipo. */
    @GetMapping("/technicians/{tecnicoId}/documents/{tipo}")
    public org.springframework.http.ResponseEntity<byte[]> documento(@PathVariable Long tecnicoId, @PathVariable String tipo) {
        var d = verificacion.documento(tecnicoId, tipo);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(d.tipoMime()))
                .cacheControl(org.springframework.http.CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .body(d.contenido());
    }

    @PatchMapping("/technicians/{tecnicoId}/verification")
    public VerificacionService.Estado decidir(@PathVariable Long tecnicoId,
            @Valid @RequestBody Peticiones.DecisionVerificacion d, @AuthenticationPrincipal Jwt jwt) {
        var e = verificacion.decidir(id(jwt), tecnicoId, "APPROVED".equals(d.decision()), d.motivo());
        if (e.avisoId() != null) {
            notificaciones.despachar(e.avisoId()); // después del COMMIT, fuera de la transacción
        }
        return e.valor();
    }

    private static Long id(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }

    @GetMapping("/summary")
    public Map<String, Object> resumen() {
        return admin.resumen();
    }

    @GetMapping("/users")
    public AdministracionService.Pagina<AdministracionService.UsuarioAdmin> usuarios(
            @RequestParam(required = false) String rol, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return admin.usuarios(rol, q, pagina, tamano);
    }

    /** Suspende o reactiva una cuenta: {"status":"SUSPENDED","reason":"..."} o {"status":"ACTIVE"}. */
    @PatchMapping("/users/{usuarioId}/status")
    public AdministracionService.UsuarioAdmin cambiarEstado(@PathVariable Long usuarioId,
            @Valid @RequestBody Peticiones.EstadoCuenta c, @AuthenticationPrincipal Jwt jwt) {
        if ("ACTIVE".equals(c.status())) {
            return admin.reactivar(id(jwt), usuarioId);
        }
        if (c.reason() == null || c.reason().trim().length() < 5 || c.reason().length() > 200) {
            throw new ErrorNegocio(Codigo.VALIDATION_ERROR, "reason debe tener entre 5 y 200 caracteres");
        }
        return admin.suspender(id(jwt), usuarioId, c.reason().trim());
    }

    @GetMapping("/service-requests")
    public AdministracionService.Pagina<AdministracionService.SolicitudAdmin> solicitudes(
            @RequestParam(required = false) String estado, @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return admin.solicitudes(estado, pagina, tamano);
    }

    @PatchMapping("/service-requests/{solicitudId}/cancel")
    public AdministracionService.SolicitudAdmin cancelar(@PathVariable Long solicitudId,
            @Valid @RequestBody Peticiones.MotivoAdmin m, @AuthenticationPrincipal Jwt jwt) {
        var e = admin.cancelarSolicitud(id(jwt), solicitudId, m.motivo());
        if (e.avisoId() != null) {
            notificaciones.despachar(e.avisoId()); // después del COMMIT, fuera de la transacción
        }
        return e.valor();
    }

    @GetMapping("/ledger")
    public List<AdministracionService.MovimientoAdmin> libro(@RequestParam Long solicitudId) {
        return admin.libro(solicitudId);
    }

    @GetMapping("/audit-events")
    public AdministracionService.Pagina<AdministracionService.Auditoria> auditoria(
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return admin.auditoria(pagina, tamano);
    }
}
