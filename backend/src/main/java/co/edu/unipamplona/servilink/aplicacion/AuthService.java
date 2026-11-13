package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.config.Configuracion;
import co.edu.unipamplona.servilink.dominio.Billetera;
import co.edu.unipamplona.servilink.dominio.Usuario;
import co.edu.unipamplona.servilink.repositorio.BilleteraRepositorio;
import co.edu.unipamplona.servilink.repositorio.UsuarioRepositorio;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-001 (registro), RF-003 (autenticación con JWT) y RF-009 (consentimiento). */
@Service
public class AuthService {

    /** {@code renovacion} es el token de renovación nuevo; nulo si no hay que entregar otro (ventana de gracia). */
    public record Sesion(String accessToken, long expiresIn, Long id, String rol, String nombre, String renovacion) {}

    private final UsuarioRepositorio usuarios;
    private final BilleteraRepositorio billeteras;
    private final PasswordEncoder codificador;
    private final JwtEncoder jwt;
    private final Configuracion config;
    private final Clock reloj;
    private final RenovacionSesion renovaciones;

    public AuthService(UsuarioRepositorio usuarios, BilleteraRepositorio billeteras, PasswordEncoder codificador,
                       JwtEncoder jwt, Configuracion config, Clock reloj, RenovacionSesion renovaciones) {
        this.usuarios = usuarios;
        this.billeteras = billeteras;
        this.codificador = codificador;
        this.jwt = jwt;
        this.config = config;
        this.reloj = reloj;
        this.renovaciones = renovaciones;
    }

    @Transactional
    public Usuario registrar(String rol, String nombre, String correo, String telefono, String clave) {
        String correoNormal = correo.trim().toLowerCase(Locale.ROOT);
        if (usuarios.findByCorreo(correoNormal).isPresent()) {
            throw new ErrorNegocio(Codigo.EMAIL_ALREADY_REGISTERED, "Ya existe una cuenta con ese correo");
        }
        Usuario u = usuarios.save(new Usuario(rol, nombre.trim(), correoNormal, telefono.trim(),
                codificador.encode(clave)));
        if ("TECNICO".equals(rol)) {
            billeteras.save(new Billetera(u.getId(), 0)); // RF-037: cada técnico tiene su billetera
        }
        return u;
    }

    /** Mismo mensaje para correo inexistente y clave errónea: no revela qué cuentas existen. */
    public Sesion iniciarSesion(String correo, String clave) {
        Usuario u = usuarios.findByCorreo(correo.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (u == null || !codificador.matches(clave, u.getPasswordHash())) {
            throw new ErrorNegocio(Codigo.UNAUTHENTICATED, "Correo o clave incorrectos");
        }
        if (u.isSuspendido()) {
            throw new ErrorNegocio(Codigo.ACCOUNT_SUSPENDED, "Tu cuenta está suspendida; contacta al administrador");
        }
        return emitir(u, renovaciones.abrir(u.getId()));
    }

    /** Cambia un token de renovación válido por un token de acceso nuevo (y otro token de renovación, rotado). */
    @Transactional(noRollbackFor = ErrorNegocio.class) // la revocación por reutilización debe quedar guardada aunque se responda 401
    public Sesion renovar(String tokenRenovacion) {
        var r = renovaciones.renovar(tokenRenovacion);
        Usuario u = usuarios.findById(r.usuarioId()).orElse(null);
        if (u == null || u.isSuspendido()) {
            renovaciones.revocarDe(r.usuarioId());
            throw new ErrorNegocio(Codigo.UNAUTHENTICATED, "Tu sesión venció, ingresa de nuevo");
        }
        return emitir(u, r.tokenNuevo());
    }

    public void cerrarSesion(String tokenRenovacion) {
        renovaciones.cerrar(tokenRenovacion);
    }

    public long vigenciaRenovacionSeg() {
        return renovaciones.vigenciaSeg();
    }

    private Sesion emitir(Usuario u, String renovacion) {
        var ahora = reloj.instant();
        var claims = JwtClaimsSet.builder().subject(String.valueOf(u.getId())).claim("rol", u.getRol())
                .issuedAt(ahora).expiresAt(ahora.plus(Duration.ofSeconds(config.tokenVigenciaSeg()))).build();
        String token = jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Sesion(token, config.tokenVigenciaSeg(), u.getId(), u.getRol(), u.getNombre(), renovacion);
    }

    public record Perfil(Long id, String rol, String nombre, String correo, String telefono) {}

    @Transactional(readOnly = true)
    public Perfil perfil(Long usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new ErrorNegocio(Codigo.NOT_FOUND, "Usuario inexistente"));
        return new Perfil(u.getId(), u.getRol(), u.getNombre(), u.getCorreo(), u.getTelefono());
    }

    /** Crea la cuenta de administrador inicial si no existe. Devuelve true si la creó. */
    @Transactional
    public boolean asegurarAdmin(String correo, String clave) {
        if (clave == null || clave.length() < 12) {
            throw new IllegalStateException("ADMIN_CLAVE debe tener al menos 12 caracteres");
        }
        String normal = correo.trim().toLowerCase(Locale.ROOT);
        if (usuarios.findByCorreo(normal).isPresent()) {
            return false;
        }
        usuarios.save(new Usuario("ADMIN", "Administrador", normal, "0000000", codificador.encode(clave)));
        return true;
    }
}
