package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.NotificacionService;
import co.edu.unipamplona.servilink.infraestructura.CuentaServicioGoogle;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Avisos push del navegador (Firebase Cloud Messaging). La configuración web de Firebase es pública por diseño;
 * lo secreto (la cuenta de servicio) vive solo en el servidor. Sin esa cuenta el servidor responde "habilitado: false"
 * y la interfaz no ofrece los avisos.
 */
@RestController
@RequestMapping("/api/v1")
public class PushController {

    private final NotificacionService notificaciones;
    private final CuentaServicioGoogle cuenta;
    private final Map<String, String> web;

    public PushController(NotificacionService notificaciones, CuentaServicioGoogle cuenta,
            @Value("${servilink.firebase-api-key:}") String apiKey,
            @Value("${servilink.firebase-project-id:}") String projectId,
            @Value("${servilink.firebase-sender-id:}") String senderId,
            @Value("${servilink.firebase-app-id:}") String appId,
            @Value("${servilink.firebase-vapid-key:}") String vapidKey) {
        this.notificaciones = notificaciones;
        this.cuenta = cuenta;
        this.web = Map.of("apiKey", apiKey, "projectId", projectId, "messagingSenderId", senderId,
                "appId", appId, "vapidKey", vapidKey);
    }

    /** Los avisos solo se ofrecen si hay cuenta de servicio y la configuración web completa. */
    public boolean habilitado() {
        return cuenta.habilitada() && web.values().stream().noneMatch(String::isBlank);
    }

    @GetMapping("/push/config")
    public Map<String, Object> config() {
        return habilitado()
                ? Map.of("habilitado", true, "apiKey", web.get("apiKey"), "projectId", web.get("projectId"),
                        "messagingSenderId", web.get("messagingSenderId"), "appId", web.get("appId"),
                        "vapidKey", web.get("vapidKey"))
                : Map.of("habilitado", false);
    }

    @PutMapping("/me/push-token")
    public ResponseEntity<Void> registrar(@Valid @RequestBody Peticiones.TokenPush t, @AuthenticationPrincipal Jwt jwt) {
        if (!habilitado()) {
            return ResponseEntity.noContent().build(); // sin Firebase no se guarda nada
        }
        notificaciones.registrarToken(Long.valueOf(jwt.getSubject()), t.token());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/push-token")
    public ResponseEntity<Void> quitar(@AuthenticationPrincipal Jwt jwt) {
        notificaciones.quitarToken(Long.valueOf(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}
