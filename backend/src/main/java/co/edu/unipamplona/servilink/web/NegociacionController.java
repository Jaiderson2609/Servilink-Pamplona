package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.Codigo;
import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import co.edu.unipamplona.servilink.aplicacion.NegociacionService;
import co.edu.unipamplona.servilink.aplicacion.SolicitudService;
import co.edu.unipamplona.servilink.dominio.Usuario;
import co.edu.unipamplona.servilink.repositorio.UsuarioRepositorio;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Negociación de precio, retiro de postulaciones y disponibilidad del técnico (DDS 9). */
@RestController
@RequestMapping("/api/v1")
public class NegociacionController {

    private final NegociacionService negociacion;
    private final SolicitudService solicitudes;
    private final UsuarioRepositorio usuarios;

    public NegociacionController(NegociacionService negociacion, SolicitudService solicitudes, UsuarioRepositorio usuarios) {
        this.negociacion = negociacion;
        this.solicitudes = solicitudes;
        this.usuarios = usuarios;
    }

    private static Long id(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }

    @PostMapping("/applications/{applicationId}/offers")
    public ResponseEntity<SolicitudService.VistaOferta> ofertar(@PathVariable Long applicationId,
            @Valid @RequestBody Peticiones.OfertaCrear o, @AuthenticationPrincipal Jwt jwt) {
        var v = negociacion.ofertar(id(jwt), jwt.getClaimAsString("rol"), applicationId, o.montoCop());
        return ResponseEntity.created(URI.create("/api/v1/offers/" + v.id())).body(v);
    }

    @PatchMapping("/offers/{offerId}/accept")
    public SolicitudService.VistaOferta aceptar(@PathVariable Long offerId, @AuthenticationPrincipal Jwt jwt) {
        return negociacion.aceptar(id(jwt), offerId);
    }

    @PatchMapping("/applications/{applicationId}/withdraw")
    public Map<String, Object> retirar(@PathVariable Long applicationId, @AuthenticationPrincipal Jwt jwt) {
        return Map.of("id", applicationId, "estado", negociacion.retirar(id(jwt), applicationId));
    }

    @GetMapping("/service-requests/{solicitudId}/applications/mine")
    public SolicitudService.VistaPostulacion miPostulacion(@PathVariable Long solicitudId, @AuthenticationPrincipal Jwt jwt) {
        return solicitudes.miPostulacion(id(jwt), solicitudId);
    }

    @GetMapping("/technicians/me/availability")
    @Transactional(readOnly = true)
    public Map<String, String> disponibilidad(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("estado", usuario(jwt).getDisponibilidad());
    }

    @PatchMapping("/technicians/me/availability")
    @Transactional
    public Map<String, String> cambiarDisponibilidad(@Valid @RequestBody Peticiones.Disponibilidad d,
                                                     @AuthenticationPrincipal Jwt jwt) {
        usuario(jwt).setDisponibilidad(d.estado());
        return Map.of("estado", d.estado());
    }

    private Usuario usuario(Jwt jwt) {
        return usuarios.findById(id(jwt)).orElseThrow(() -> new ErrorNegocio(Codigo.NOT_FOUND, "Usuario inexistente"));
    }
}
