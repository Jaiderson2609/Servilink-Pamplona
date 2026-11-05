package co.edu.unipamplona.servilink.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Idempotency-Key (DDS DT-10). Solo actúa en las operaciones que crean o cambian estado/dinero y solo si el cliente
 * envía la cabecera. Reserva la clave antes de ejecutar, guarda la respuesta exitosa y la repite en los reintentos.
 * Las respuestas de error no se guardan: un reintento después de corregir el problema sí se ejecuta.
 */
public class FiltroIdempotencia extends OncePerRequestFilter {

    static final String CABECERA = "Idempotency-Key";
    static final Duration VIGENCIA = Duration.ofHours(24);
    static final Duration ABANDONO = Duration.ofMinutes(2);
    private static final Pattern FORMA = Pattern.compile("^[A-Za-z0-9_-]{8,80}$");
    private static final AntPathMatcher RUTAS = new AntPathMatcher();
    private static final Map<String, List<String>> MARCADAS = Map.of(
            "POST", List.of("/api/v1/service-requests", "/api/v1/service-requests/*/applications", "/api/v1/wallet/topups"),
            "PATCH", List.of("/api/v1/applications/*/select"));

    private final JdbcTemplate jdbc;
    private final Clock reloj;

    public FiltroIdempotencia(JdbcTemplate jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        if (req.getHeader(CABECERA) == null) {
            return true;
        }
        return MARCADAS.getOrDefault(req.getMethod(), List.of()).stream().noneMatch(p -> RUTAS.match(p, req.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain cadena)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            cadena.doFilter(req, res); // la seguridad ya decidió; sin usuario no hay clave que guardar
            return;
        }
        String clave = req.getHeader(CABECERA);
        if (!FORMA.matcher(clave).matches()) {
            error(res, 400, "VALIDATION_ERROR", "Idempotency-Key debe tener entre 8 y 80 caracteres (letras, números, - y _)");
            return;
        }
        long usuario = Long.parseLong(auth.getName());
        String huella = req.getMethod() + " " + req.getRequestURI();

        if (!reservar(usuario, clave, huella)) {
            repetir(usuario, clave, huella, res);
            return;
        }
        var envuelta = new ContentCachingResponseWrapper(res);
        try {
            cadena.doFilter(req, envuelta);
        } catch (IOException | ServletException | RuntimeException e) {
            liberar(usuario, clave);
            throw e;
        }
        int estado = envuelta.getStatus();
        if (estado >= 200 && estado < 300) {
            guardar(usuario, clave, estado, new String(envuelta.getContentAsByteArray(), StandardCharsets.UTF_8),
                    envuelta.getContentType());
        } else {
            liberar(usuario, clave);
        }
        envuelta.copyBodyToResponse();
    }

    /** true si esta petición es la primera con la clave (o la anterior venció o quedó abandonada). */
    private boolean reservar(long usuario, String clave, String huella) {
        for (int intento = 0; intento < 2; intento++) {
            int n = jdbc.update("INSERT INTO claves_idempotencia (usuario_id, clave, huella, estado, creada_en) "
                    + "VALUES (?, ?, ?, 'EN_CURSO', ?) ON CONFLICT DO NOTHING", usuario, clave, huella, ahora());
            if (n == 1) {
                return true;
            }
            // Una clave vencida (24 h) o una reserva abandonada por una caída se pueden reutilizar.
            int borradas = jdbc.update("DELETE FROM claves_idempotencia WHERE usuario_id = ? AND clave = ? AND "
                    + "((estado = 'COMPLETADA' AND creada_en < ?) OR (estado = 'EN_CURSO' AND creada_en < ?))",
                    usuario, clave, Timestamp.from(reloj.instant().minus(VIGENCIA)),
                    Timestamp.from(reloj.instant().minus(ABANDONO)));
            if (borradas == 0) {
                return false;
            }
        }
        return false;
    }

    private void repetir(long usuario, String clave, String huella, HttpServletResponse res) throws IOException {
        for (int espera = 0; espera < 50; espera++) { // hasta ~5 s si la primera petición sigue ejecutándose
            var filas = jdbc.queryForList("SELECT huella, estado, http_estado, cuerpo, tipo FROM claves_idempotencia "
                    + "WHERE usuario_id = ? AND clave = ?", usuario, clave);
            if (filas.isEmpty()) { // la primera falló y liberó la clave
                error(res, 409, "CONFLICT", "La operación anterior con esa clave no terminó; vuelve a intentarlo");
                return;
            }
            var f = filas.get(0);
            if (!huella.equals(f.get("huella"))) {
                error(res, 400, "VALIDATION_ERROR", "Esa Idempotency-Key ya se usó en otra operación");
                return;
            }
            if ("COMPLETADA".equals(f.get("estado"))) {
                res.setStatus(((Number) f.get("http_estado")).intValue());
                if (f.get("tipo") != null) {
                    res.setContentType((String) f.get("tipo"));
                }
                res.setHeader("Idempotent-Replay", "true");
                res.getOutputStream().write(String.valueOf(f.get("cuerpo")).getBytes(StandardCharsets.UTF_8));
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        error(res, 409, "CONFLICT", "La operación con esa clave sigue en curso; espera un momento");
    }

    private void guardar(long usuario, String clave, int estado, String cuerpo, String tipo) {
        jdbc.update("UPDATE claves_idempotencia SET estado = 'COMPLETADA', http_estado = ?, cuerpo = ?, tipo = ? "
                + "WHERE usuario_id = ? AND clave = ?", estado, cuerpo, tipo, usuario, clave);
    }

    private void liberar(long usuario, String clave) {
        jdbc.update("DELETE FROM claves_idempotencia WHERE usuario_id = ? AND clave = ?", usuario, clave);
    }

    private Timestamp ahora() {
        return Timestamp.from(reloj.instant());
    }

    private static void error(HttpServletResponse res, int estado, String codigo, String mensaje) throws IOException {
        res.setStatus(estado);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"code\":\"" + codigo + "\",\"message\":\"" + mensaje + "\",\"traceId\":\""
                + UUID.randomUUID() + "\"}");
    }
}
