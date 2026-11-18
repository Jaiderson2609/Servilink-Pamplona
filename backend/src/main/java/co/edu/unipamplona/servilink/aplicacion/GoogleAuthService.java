package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.repositorio.UsuarioRepositorio;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Ingreso y registro con Google. Google solo prueba quién es la persona; la sesión es la misma de siempre. */
@Service
public class GoogleAuthService {

    private static final Pattern TELEFONO = Pattern.compile("^[0-9 +]{7,20}$");
    private static final SecureRandom AZAR = new SecureRandom();

    private final VerificadorGoogle verificador;
    private final UsuarioRepositorio usuarios;
    private final AuthService auth;

    public GoogleAuthService(VerificadorGoogle verificador, UsuarioRepositorio usuarios, AuthService auth) {
        this.verificador = verificador;
        this.usuarios = usuarios;
        this.auth = auth;
    }

    public String clienteId() {
        return verificador.clienteId();
    }

    /**
     * Si el correo ya tiene cuenta, abre su sesión. Si no, crea la cuenta con el rol, el teléfono y el consentimiento
     * recibidos; mientras falten, responde GOOGLE_REGISTRATION_REQUIRED para que la pantalla los pida.
     */
    public AuthService.Sesion ingresar(String credencial, String rol, String telefono, boolean aceptaTerminos) {
        var identidad = verificador.verificar(credencial);
        String correo = identidad.correo().trim().toLowerCase(Locale.ROOT);
        var existente = usuarios.findByCorreo(correo);
        if (existente.isPresent()) {
            return auth.abrirSesion(existente.get());
        }
        if (rol == null || telefono == null || !aceptaTerminos) {
            throw new ErrorNegocio(Codigo.GOOGLE_REGISTRATION_REQUIRED,
                    "Para crear tu cuenta indica si eres hogar o técnico, tu teléfono y acepta los términos");
        }
        if (!TELEFONO.matcher(telefono.trim()).matches()) {
            throw new ErrorNegocio(Codigo.VALIDATION_ERROR, "telefono no es válido");
        }
        String nombre = identidad.nombre().trim();
        // La cuenta nace con una clave aleatoria que nadie conoce: solo se entra con Google.
        byte[] bytes = new byte[33];
        AZAR.nextBytes(bytes);
        var u = auth.registrar(rol, nombre.length() > 80 ? nombre.substring(0, 80) : nombre, correo, telefono,
                Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        return auth.abrirSesion(u);
    }
}
