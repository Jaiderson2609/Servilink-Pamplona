package co.edu.unipamplona.servilink.infraestructura;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Canal de eventos en tiempo real (/ws/v1/eventos). El navegador no puede enviar el encabezado Authorization en un
 * WebSocket, así que el cliente se autentica con su primer mensaje {"tipo":"auth","token":"..."} (el JWT no viaja en la URL).
 * Solo sale de aquí un aviso sin datos; el contenido se pide por la API REST, que es la que autoriza.
 * El registro de conexiones vive en la memoria de esta instancia: con varias instancias haría falta un bus compartido.
 */
@Component
public class ManejadorEventos extends TextWebSocketHandler {

    static final Logger LOG = LoggerFactory.getLogger(ManejadorEventos.class);
    static final int MAX_CONEXIONES_POR_USUARIO = 5;
    static final Duration PLAZO_AUTENTICACION = Duration.ofSeconds(5);
    static final int LIMITE_ENVIO_MS = 5_000;
    static final int LIMITE_BUFER_BYTES = 16 * 1024;

    /** Estado de una conexión: anónima hasta recibir un token válido. */
    private static final class Conexion {
        final WebSocketSession sesion;
        final long orden; // orden de llegada: decide cuáles son "las más viejas"
        volatile Long usuarioId;
        volatile String rol;
        volatile ScheduledFuture<?> plazoAutenticacion;
        volatile ScheduledFuture<?> vencimientoToken;

        Conexion(WebSocketSession sesion, long orden) { this.sesion = sesion; this.orden = orden; }
    }

    private final JwtDecoder decodificador;
    private final Clock reloj;
    private final ScheduledExecutorService temporizador;
    private final Map<String, Conexion> conexiones = new ConcurrentHashMap<>();
    private final AtomicLong llegadas = new AtomicLong();

    @org.springframework.beans.factory.annotation.Autowired
    public ManejadorEventos(JwtDecoder decodificador, Clock reloj) {
        this(decodificador, reloj, Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ws-eventos-plazos");
            t.setDaemon(true);
            return t;
        }));
    }

    ManejadorEventos(JwtDecoder decodificador, Clock reloj, ScheduledExecutorService temporizador) {
        this.decodificador = decodificador;
        this.reloj = reloj;
        this.temporizador = temporizador;
    }

    @PreDestroy
    void apagar() {
        temporizador.shutdownNow();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession sesion) {
        var segura = new ConcurrentWebSocketSessionDecorator(sesion, LIMITE_ENVIO_MS, LIMITE_BUFER_BYTES);
        var c = new Conexion(segura, llegadas.incrementAndGet());
        conexiones.put(sesion.getId(), c);
        c.plazoAutenticacion = temporizador.schedule(() -> {
            if (c.usuarioId == null) {
                cerrar(c, CloseStatus.POLICY_VIOLATION.withReason("No se autenticó a tiempo"));
            }
        }, PLAZO_AUTENTICACION.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    protected void handleTextMessage(WebSocketSession sesion, TextMessage mensaje) {
        Conexion c = conexiones.get(sesion.getId());
        if (c == null) {
            return;
        }
        Map<String, Object> cuerpo;
        try {
            cuerpo = JsonParserFactory.getJsonParser().parseMap(mensaje.getPayload());
        } catch (RuntimeException e) {
            if (c.usuarioId == null) {
                cerrar(c, CloseStatus.POLICY_VIOLATION.withReason("Mensaje inválido"));
            }
            return;
        }
        Object tipo = cuerpo.get("tipo");
        if (c.usuarioId == null) {
            autenticar(c, tipo, cuerpo.get("token"));
        } else if ("ping".equals(tipo)) {
            enviar(c, "{\"tipo\":\"pong\"}");
        }
    }

    private void autenticar(Conexion c, Object tipo, Object token) {
        if (!"auth".equals(tipo) || !(token instanceof String texto) || texto.isBlank()) {
            cerrar(c, CloseStatus.POLICY_VIOLATION.withReason("Se esperaba el mensaje de autenticación"));
            return;
        }
        Jwt jwt;
        try {
            jwt = decodificador.decode(texto); // mismo decodificador que la API: vencimiento y cuenta suspendida
        } catch (JwtException e) {
            cerrar(c, CloseStatus.POLICY_VIOLATION.withReason("Token inválido"));
            return;
        }
        Long usuario;
        try {
            usuario = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            cerrar(c, CloseStatus.POLICY_VIOLATION.withReason("Token inválido"));
            return;
        }
        c.rol = jwt.getClaimAsString("rol");
        c.usuarioId = usuario;
        if (c.plazoAutenticacion != null) {
            c.plazoAutenticacion.cancel(false);
        }
        Instant vence = jwt.getExpiresAt();
        if (vence != null) { // el socket no sobrevive a su token: el cliente reconecta con uno vigente
            long ms = Math.max(0, Duration.between(reloj.instant(), vence).toMillis());
            c.vencimientoToken = temporizador.schedule(
                    () -> cerrar(c, CloseStatus.POLICY_VIOLATION.withReason("Token vencido")), ms, TimeUnit.MILLISECONDS);
        }
        limitarConexiones(usuario, c);
        enviar(c, "{\"tipo\":\"listo\"}");
    }

    /** Un usuario no mantiene más de MAX_CONEXIONES_POR_USUARIO conexiones: se cierran las más viejas. */
    private void limitarConexiones(Long usuario, Conexion nueva) {
        List<Conexion> propias = new ArrayList<>();
        for (Conexion c : conexiones.values()) {
            if (usuario.equals(c.usuarioId) && c != nueva) {
                propias.add(c);
            }
        }
        propias.sort(Comparator.comparingLong(x -> x.orden));
        int sobran = propias.size() + 1 - MAX_CONEXIONES_POR_USUARIO;
        for (int i = 0; i < sobran; i++) {
            cerrar(propias.get(i), CloseStatus.POLICY_VIOLATION.withReason("Demasiadas conexiones"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession sesion, CloseStatus estado) {
        retirar(sesion.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession sesion, Throwable error) {
        Conexion c = conexiones.get(sesion.getId());
        if (c != null) {
            cerrar(c, CloseStatus.SERVER_ERROR);
        }
    }

    /** Avisa a todas las conexiones autenticadas de un usuario. */
    public void avisarUsuario(Long usuarioId, String mensaje) {
        if (usuarioId == null) {
            return;
        }
        for (Conexion c : conexiones.values()) {
            if (usuarioId.equals(c.usuarioId)) {
                enviar(c, mensaje);
            }
        }
    }

    /** Avisa a todas las conexiones autenticadas con un rol (por ejemplo, los técnicos). */
    public void avisarRol(String rol, String mensaje) {
        for (Conexion c : conexiones.values()) {
            if (c.usuarioId != null && rol.equals(c.rol)) {
                enviar(c, mensaje);
            }
        }
    }

    int conexionesAutenticadas() {
        return (int) conexiones.values().stream().filter(c -> c.usuarioId != null).count();
    }

    private void enviar(Conexion c, String texto) {
        try {
            if (c.sesion.isOpen()) {
                c.sesion.sendMessage(new TextMessage(texto));
            }
        } catch (IOException | RuntimeException e) {
            LOG.debug("No se pudo avisar a la conexión {}: {}", c.sesion.getId(), e.toString());
            cerrar(c, CloseStatus.SERVER_ERROR);
        }
    }

    private void cerrar(Conexion c, CloseStatus estado) {
        retirar(c.sesion.getId());
        try {
            c.sesion.close(estado);
        } catch (IOException | RuntimeException e) {
            LOG.debug("Error al cerrar la conexión {}: {}", c.sesion.getId(), e.toString());
        }
    }

    private void retirar(String id) {
        Conexion c = conexiones.remove(id);
        if (c != null) {
            if (c.plazoAutenticacion != null) {
                c.plazoAutenticacion.cancel(false);
            }
            if (c.vencimientoToken != null) {
                c.vencimientoToken.cancel(false);
            }
        }
    }
}
