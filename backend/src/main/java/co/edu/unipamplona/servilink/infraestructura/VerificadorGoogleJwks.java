package co.edu.unipamplona.servilink.infraestructura;

import co.edu.unipamplona.servilink.aplicacion.Codigo;
import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import co.edu.unipamplona.servilink.aplicacion.VerificadorGoogle;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifica el ID token de Google con las llaves públicas de Google: firma, emisor, audiencia (nuestro ID de cliente),
 * vigencia y correo verificado. No usa el secreto de cliente.
 */
@Component
public class VerificadorGoogleJwks implements VerificadorGoogle {

    static final String LLAVES = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> EMISORES = List.of("https://accounts.google.com", "accounts.google.com");

    private final String clienteId;
    private final NimbusJwtDecoder decodificador;

    @Autowired
    public VerificadorGoogleJwks(@Value("${servilink.google-client-id:}") String clienteId) {
        this(clienteId, clienteId == null || clienteId.isBlank() ? null : NimbusJwtDecoder.withJwkSetUri(LLAVES).build());
    }

    VerificadorGoogleJwks(String clienteId, NimbusJwtDecoder decodificador) {
        this.clienteId = clienteId == null ? "" : clienteId.trim();
        this.decodificador = decodificador;
        if (decodificador != null) {
            OAuth2TokenValidator<Jwt> emisor = j -> EMISORES.contains(j.getClaimAsString("iss"))
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Emisor no válido", null));
            OAuth2TokenValidator<Jwt> audiencia = j -> j.getAudience() != null && j.getAudience().contains(this.clienteId)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audiencia no válida", null));
            decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), emisor, audiencia));
        }
    }

    @Override
    public boolean disponible() {
        return decodificador != null;
    }

    @Override
    public String clienteId() {
        return clienteId;
    }

    @Override
    public Identidad verificar(String credencial) {
        if (!disponible()) {
            throw new ErrorNegocio(Codigo.GOOGLE_UNAVAILABLE, "El ingreso con Google no está disponible por ahora");
        }
        Jwt jwt;
        try {
            jwt = decodificador.decode(credencial);
        } catch (JwtException | IllegalArgumentException e) {
            throw invalida();
        }
        String correo = jwt.getClaimAsString("email");
        if (correo == null || correo.isBlank() || !Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
            throw invalida();
        }
        String nombre = jwt.getClaimAsString("name");
        return new Identidad(correo, nombre == null || nombre.isBlank() ? correo.substring(0, correo.indexOf('@')) : nombre);
    }

    private static ErrorNegocio invalida() {
        return new ErrorNegocio(Codigo.UNAUTHENTICATED, "No se pudo verificar tu cuenta de Google. Intenta de nuevo");
    }
}
