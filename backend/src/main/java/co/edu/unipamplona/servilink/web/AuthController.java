package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.AuthService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** La cookie de renovación solo viaja a /auth: ninguna otra petición la lleva. */
    static final String COOKIE = "servilink_renovacion";
    static final String RUTA_COOKIE = "/api/v1/auth";

    private final AuthService auth;
    private final boolean cookieSegura;

    public AuthController(AuthService auth, @Value("${servilink.cookie-segura:true}") boolean cookieSegura) {
        this.auth = auth;
        this.cookieSegura = cookieSegura;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registro(@Valid @RequestBody Peticiones.Registro r) {
        var u = auth.registrar(r.rol(), r.nombre(), r.correo(), r.telefono(), r.clave());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", u.getId(), "rol", u.getRol()));
    }

    /** Respuesta con la forma de TokenResponse del contrato OpenAPI; el token de renovación va en una cookie HttpOnly. */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody Peticiones.Login l) {
        return conSesion(auth.iniciarSesion(l.correo(), l.clave()));
    }

    /** Cambia la cookie de renovación por un token de acceso nuevo y rota la cookie. */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> renovar(@CookieValue(name = COOKIE, required = false) String renovacion) {
        return conSesion(auth.renovar(renovacion));
    }

    /** Cierra la sesión en el servidor (revoca la renovación) y borra la cookie. */
    @PostMapping("/logout")
    public ResponseEntity<Void> salir(@CookieValue(name = COOKIE, required = false) String renovacion) {
        auth.cerrarSesion(renovacion);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }

    private ResponseEntity<Map<String, Object>> conSesion(AuthService.Sesion s) {
        var cuerpo = Map.<String, Object>of("accessToken", s.accessToken(), "tokenType", "Bearer", "expiresIn", s.expiresIn(),
                "usuarioId", s.id(), "rol", s.rol(), "nombre", s.nombre());
        var r = ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (s.renovacion() != null) {
            r.header(HttpHeaders.SET_COOKIE, cookie(s.renovacion(), Duration.ofSeconds(auth.vigenciaRenovacionSeg())).toString());
        }
        return r.body(cuerpo);
    }

    private ResponseCookie cookie(String valor, Duration vida) {
        return ResponseCookie.from(COOKIE, valor).httpOnly(true).secure(cookieSegura).sameSite("Strict")
                .path(RUTA_COOKIE).maxAge(vida).build();
    }
}
