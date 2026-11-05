package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.AuthService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registro(@Valid @RequestBody Peticiones.Registro r) {
        var u = auth.registrar(r.rol(), r.nombre(), r.correo(), r.telefono(), r.clave());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", u.getId(), "rol", u.getRol()));
    }

    /** Respuesta con la forma de TokenResponse del contrato OpenAPI. */
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody Peticiones.Login l) {
        var s = auth.iniciarSesion(l.correo(), l.clave());
        return Map.of("accessToken", s.accessToken(), "tokenType", "Bearer", "expiresIn", s.expiresIn(),
                "usuarioId", s.id(), "rol", s.rol(), "nombre", s.nombre());
    }
}
