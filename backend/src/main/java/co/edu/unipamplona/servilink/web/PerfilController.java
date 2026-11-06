package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.AuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PerfilController {

    private final AuthService auth;

    public PerfilController(AuthService auth) {
        this.auth = auth;
    }

    /** Perfil del usuario autenticado (DDS 9: GET /me). */
    @GetMapping("/me")
    public AuthService.Perfil yo(@AuthenticationPrincipal Jwt jwt) {
        return auth.perfil(Long.valueOf(jwt.getSubject()));
    }
}
