package co.edu.unipamplona.servilink.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** Prueba unitaria sin servidor ni base de datos: autenticación por primer mensaje, plazos, alcance de los avisos y límites. */
class ManejadorEventosTest {

    static final Instant AHORA = Instant.parse("2026-11-12T12:00:00Z");

    ScheduledExecutorService temporizador;
    ManejadorEventos manejador;
    int contadorSesiones;

    @BeforeEach
    void preparar() {
        temporizador = mock(ScheduledExecutorService.class);
        when(temporizador.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenReturn(mock(ScheduledFuture.class));
        JwtDecoder decodificador = token -> switch (token) {
            case "hogar-7" -> jwt("7", "HOGAR", AHORA.plusSeconds(3600));
            case "tecnico-9" -> jwt("9", "TECNICO", AHORA.plusSeconds(3600));
            case "tecnico-10" -> jwt("10", "TECNICO", AHORA.plusSeconds(3600));
            case "sin-sub-numerico" -> jwt("abc", "HOGAR", AHORA.plusSeconds(3600));
            default -> throw new BadJwtException("inválido");
        };
        manejador = new ManejadorEventos(decodificador, Clock.fixed(AHORA, ZoneOffset.UTC), temporizador);
    }

    static Jwt jwt(String sub, String rol, Instant vence) {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject(sub).claim("rol", rol)
                .issuedAt(AHORA).expiresAt(vence).build();
    }

    WebSocketSession conectar() {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn("s" + (++contadorSesiones));
        when(s.isOpen()).thenReturn(true);
        manejador.afterConnectionEstablished(s);
        return s;
    }

    void autenticar(WebSocketSession s, String token) {
        manejador.handleTextMessage(s, new TextMessage("{\"tipo\":\"auth\",\"token\":\"" + token + "\"}"));
    }

    WebSocketSession conectadoComo(String token) {
        WebSocketSession s = conectar();
        autenticar(s, token);
        return s;
    }

    static void recibio(WebSocketSession s, String texto) throws IOException {
        verify(s).sendMessage(new TextMessage(texto));
    }

    @Test
    void conTokenValidoResponderListo() throws Exception {
        WebSocketSession s = conectadoComo("hogar-7");
        recibio(s, "{\"tipo\":\"listo\"}");
        assertThat(manejador.conexionesAutenticadas()).isEqualTo(1);
    }

    @Test
    void conTokenInvalidoSeCierraYNoRecibeAvisos() throws Exception {
        WebSocketSession s = conectadoComo("falso");
        ArgumentCaptor<CloseStatus> estado = ArgumentCaptor.forClass(CloseStatus.class);
        verify(s).close(estado.capture());
        assertThat(estado.getValue().getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
        manejador.avisarRol("HOGAR", "x");
        verify(s, never()).sendMessage(any());
        assertThat(manejador.conexionesAutenticadas()).isZero();
    }

    @Test
    void unTokenSinUsuarioNumericoSeRechaza() throws Exception {
        WebSocketSession s = conectadoComo("sin-sub-numerico");
        verify(s).close(any(CloseStatus.class));
        assertThat(manejador.conexionesAutenticadas()).isZero();
    }

    @Test
    void elPrimerMensajeDebeSerLaAutenticacion() throws Exception {
        WebSocketSession s = conectar();
        manejador.handleTextMessage(s, new TextMessage("{\"tipo\":\"ping\"}"));
        verify(s).close(any(CloseStatus.class));
        WebSocketSession otra = conectar();
        manejador.handleTextMessage(otra, new TextMessage("esto no es json"));
        verify(otra).close(any(CloseStatus.class));
    }

    @Test
    void unaConexionSinAutenticarNoRecibeAvisos() throws Exception {
        WebSocketSession s = conectar();
        manejador.avisarUsuario(7L, "x");
        manejador.avisarRol("TECNICO", "x");
        verify(s, never()).sendMessage(any());
    }

    @Test
    void elAvisoAUnUsuarioSoloLlegaASusConexiones() throws Exception {
        WebSocketSession hogar = conectadoComo("hogar-7");
        WebSocketSession hogarOtraPestana = conectadoComo("hogar-7");
        WebSocketSession tecnico = conectadoComo("tecnico-9");
        manejador.avisarUsuario(7L, "{\"tipo\":\"solicitud\",\"id\":42}");
        recibio(hogar, "{\"tipo\":\"solicitud\",\"id\":42}");
        recibio(hogarOtraPestana, "{\"tipo\":\"solicitud\",\"id\":42}");
        verify(tecnico, never()).sendMessage(new TextMessage("{\"tipo\":\"solicitud\",\"id\":42}"));
    }

    @Test
    void elAvisoPorRolSoloLlegaATecnicos() throws Exception {
        WebSocketSession hogar = conectadoComo("hogar-7");
        WebSocketSession t1 = conectadoComo("tecnico-9");
        WebSocketSession t2 = conectadoComo("tecnico-10");
        manejador.avisarRol("TECNICO", "{\"tipo\":\"abiertas\"}");
        recibio(t1, "{\"tipo\":\"abiertas\"}");
        recibio(t2, "{\"tipo\":\"abiertas\"}");
        verify(hogar, never()).sendMessage(new TextMessage("{\"tipo\":\"abiertas\"}"));
    }

    @Test
    void elLatidoSeResponde() throws Exception {
        WebSocketSession s = conectadoComo("hogar-7");
        manejador.handleTextMessage(s, new TextMessage("{\"tipo\":\"ping\"}"));
        recibio(s, "{\"tipo\":\"pong\"}");
    }

    @Test
    void sinAutenticarAntesDelPlazoSeCierra() throws Exception {
        WebSocketSession s = conectar();
        ArgumentCaptor<Runnable> tarea = ArgumentCaptor.forClass(Runnable.class);
        verify(temporizador).schedule(tarea.capture(), eq(5000L), eq(TimeUnit.MILLISECONDS));
        tarea.getValue().run();
        verify(s).close(any(CloseStatus.class));
    }

    @Test
    void siYaSeAutenticoElPlazoNoLaCierra() throws Exception {
        WebSocketSession s = conectar();
        ArgumentCaptor<Runnable> plazo = ArgumentCaptor.forClass(Runnable.class);
        verify(temporizador).schedule(plazo.capture(), eq(5000L), eq(TimeUnit.MILLISECONDS));
        autenticar(s, "hogar-7");
        plazo.getValue().run();
        verify(s, never()).close(any(CloseStatus.class));
    }

    @Test
    void laConexionSeCierraCuandoVenceElToken() throws Exception {
        WebSocketSession s = conectadoComo("hogar-7");
        ArgumentCaptor<Runnable> tarea = ArgumentCaptor.forClass(Runnable.class);
        verify(temporizador).schedule(tarea.capture(), eq(3_600_000L), eq(TimeUnit.MILLISECONDS));
        tarea.getValue().run();
        ArgumentCaptor<CloseStatus> estado = ArgumentCaptor.forClass(CloseStatus.class);
        verify(s).close(estado.capture());
        assertThat(estado.getValue().getReason()).isEqualTo("Token vencido");
        assertThat(manejador.conexionesAutenticadas()).isZero();
    }

    @Test
    void unUsuarioNoPasaDeCincoConexiones() throws Exception {
        List<WebSocketSession> sesiones = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            sesiones.add(conectadoComo("hogar-7"));
        }
        verify(sesiones.get(0)).close(any(CloseStatus.class));
        for (int i = 1; i < 6; i++) {
            verify(sesiones.get(i), never()).close(any(CloseStatus.class));
        }
        assertThat(manejador.conexionesAutenticadas()).isEqualTo(5);
    }

    @Test
    void unaConexionCerradaYaNoRecibeAvisos() throws Exception {
        WebSocketSession s = conectadoComo("hogar-7");
        manejador.afterConnectionClosed(s, CloseStatus.NORMAL);
        manejador.avisarUsuario(7L, "x");
        verify(s, never()).sendMessage(new TextMessage("x"));
        assertThat(manejador.conexionesAutenticadas()).isZero();
    }

    @Test
    void siUnaConexionFallaLasDemasSiguenRecibiendo() throws Exception {
        WebSocketSession rota = conectadoComo("tecnico-9");
        WebSocketSession sana = conectadoComo("tecnico-10");
        doThrow(new IOException("socket roto")).when(rota).sendMessage(new TextMessage("{\"tipo\":\"abiertas\"}"));
        manejador.avisarRol("TECNICO", "{\"tipo\":\"abiertas\"}");
        recibio(sana, "{\"tipo\":\"abiertas\"}");
        verify(rota, times(1)).close(any(CloseStatus.class));
        assertThat(manejador.conexionesAutenticadas()).isEqualTo(1);
    }
}
