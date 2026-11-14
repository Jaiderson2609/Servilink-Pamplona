package co.edu.unipamplona.servilink.apoyo;

import static co.edu.unipamplona.servilink.apoyo.ConfiguracionPruebas.T0;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base de las pruebas con PostgreSQL real: el bloqueo de filas y los índices parciales no existen en H2,
 * así que sin PostgreSQL las pruebas se omiten (variable TEST_POSTGRES_URL o servidor local).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfiguracionPruebas.class)
public abstract class PruebaIntegracion {

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected RelojMutable reloj;
    @Autowired protected NotificadorFalso notificador;
    @Value("${servilink.jwt-secret}") String secreto;

    @BeforeAll
    static void requierePostgres() {
        String url = System.getenv().getOrDefault("TEST_POSTGRES_URL", "jdbc:postgresql://localhost:5432/servilink_test");
        String usuario = System.getenv().getOrDefault("TEST_POSTGRES_USER", "postgres");
        String clave = System.getenv().getOrDefault("TEST_POSTGRES_PASSWORD", "postgres");
        boolean disponible;
        try (var c = DriverManager.getConnection(url, usuario, clave)) {
            disponible = c.isValid(2);
        } catch (Exception e) {
            disponible = false;
            System.err.println("AVISO: no se pudo conectar a " + url + " con el usuario '" + usuario + "': " + e);
        }
        Assumptions.assumeTrue(disponible, "PostgreSQL no disponible: se omiten las pruebas de integración");
    }

    @BeforeEach
    void limpiar() {
        jdbc.execute("TRUNCATE notificaciones_pendientes, libro_contable, postulaciones, solicitudes, "
                + "billeteras, usuarios RESTART IDENTITY CASCADE");
        reloj.fijar(T0);
        notificador.reiniciar();
    }

    protected long usuario(String rol, String nombre, String telefono) {
        return jdbc.queryForObject("INSERT INTO usuarios (rol, nombre, correo, telefono, password_hash) "
                        + "VALUES (?, ?, ?, ?, 'x') RETURNING id", Long.class,
                rol, nombre, nombre.toLowerCase().replace(' ', '.') + "@prueba.co", telefono);
    }

    protected long tecnico(String nombre, long saldo) {
        long id = usuario("TECNICO", nombre, "300" + (1000000 + (int) (Math.random() * 8999999)));
        jdbc.update("INSERT INTO billeteras (tecnico_id, disponible) VALUES (?, ?)", id, saldo);
        jdbc.update("UPDATE usuarios SET verificacion = 'APROBADA' WHERE id = ?", id); // el técnico de prueba ya está verificado
        return id;
    }

    protected long solicitud(long hogarId, String estado, Instant expiraEn) {
        return jdbc.queryForObject("INSERT INTO solicitudes (hogar_id, oficio_id, descripcion, estado, expira_en) "
                + "VALUES (?, 2, 'Cambiar un tomacorriente', ?, ?) RETURNING id", Long.class,
                hogarId, estado, java.sql.Timestamp.from(expiraEn));
    }

    protected long postulacion(long solicitudId, long tecnicoId, int precio) {
        return jdbc.queryForObject("INSERT INTO postulaciones (solicitud_id, tecnico_id, precio) "
                + "VALUES (?, ?, ?) RETURNING id", Long.class, solicitudId, tecnicoId, precio);
    }

    protected String token(String rol, long sub) {
        var clave = new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var codificador = new NimbusJwtEncoder(new ImmutableSecret<>(clave));
        Instant ahora = Instant.now();
        var claims = JwtClaimsSet.builder().subject(String.valueOf(sub)).claim("rol", rol)
                .issuedAt(ahora).expiresAt(ahora.plus(Duration.ofHours(1))).build();
        return codificador.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /** Id de postulación contenido en el cuerpo producido por cuerpo(); la API ahora lo recibe en la ruta. */
    protected static String posDe(String cuerpo) {
        var m = java.util.regex.Pattern.compile("(\\d+|abc)").matcher(cuerpo);
        return m.find() ? m.group(1) : "abc";
    }

    protected String cuerpo(long postulacionId) {
        return "{\"postulacionId\":" + postulacionId + "}";
    }
}
