package co.edu.unipamplona.servilink;

import static co.edu.unipamplona.servilink.apoyo.ConfiguracionPruebas.T0;
import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unipamplona.servilink.aplicacion.ChatService;
import co.edu.unipamplona.servilink.aplicacion.SolicitudService;
import co.edu.unipamplona.servilink.apoyo.PruebaIntegracion;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Canal en tiempo real de extremo a extremo: un servidor real, un cliente WebSocket real y transacciones reales.
 * Comprueba que el aviso llega solo a quien corresponde, solo después del COMMIT y sin datos de la solicitud.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TiempoRealTest extends PruebaIntegracion {

    @LocalServerPort int puerto;
    @Autowired SolicitudService solicitudes;
    @Autowired ChatService chat;
    @Autowired TransactionTemplate transaccion;

    long hogar, otroHogar, tecnico;
    final List<Cliente> abiertos = new ArrayList<>();

    /** Cliente WebSocket del JDK que deja los mensajes recibidos en una cola. */
    static final class Cliente implements WebSocket.Listener {
        final LinkedBlockingQueue<String> recibidos = new LinkedBlockingQueue<>();
        volatile WebSocket socket;
        volatile Integer codigoCierre;

        @Override public void onOpen(WebSocket ws) { socket = ws; ws.request(1); }

        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            recibidos.add(data.toString());
            ws.request(1);
            return null;
        }

        @Override public CompletionStage<?> onClose(WebSocket ws, int codigo, String razon) {
            codigoCierre = codigo;
            return null;
        }

        String siguiente() throws InterruptedException { return recibidos.poll(5, TimeUnit.SECONDS); }

        /** Espera un rato y confirma que no llegó nada. */
        boolean sinMensajes() throws InterruptedException { return recibidos.poll(700, TimeUnit.MILLISECONDS) == null; }
    }

    Cliente conectar(String token) throws Exception {
        var c = new Cliente();
        var ws = HttpClient.newHttpClient().newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://localhost:" + puerto + "/ws/v1/eventos"), c).get(5, TimeUnit.SECONDS);
        c.socket = ws;
        abiertos.add(c);
        if (token != null) {
            ws.sendText("{\"tipo\":\"auth\",\"token\":\"" + token + "\"}", true).get(5, TimeUnit.SECONDS);
        }
        return c;
    }

    Cliente conectado(String rol, long id) throws Exception {
        Cliente c = conectar(token(rol, id));
        assertThat(c.siguiente()).isEqualTo("{\"tipo\":\"listo\"}");
        return c;
    }

    @BeforeEach
    void escenario() {
        hogar = usuario("HOGAR", "Hogar Uno", "3110000001");
        otroHogar = usuario("HOGAR", "Hogar Dos", "3110000002");
        tecnico = tecnico("Tecnico Demo", 50_000);
    }

    @AfterEach
    void cerrar() {
        abiertos.forEach(c -> c.socket.abort());
    }

    @Test
    void alCrearUnaSolicitudAvisaAlHogarYALosTecnicosPeroNoAOtroHogar() throws Exception {
        Cliente delHogar = conectado("HOGAR", hogar);
        Cliente delTecnico = conectado("TECNICO", tecnico);
        Cliente deOtroHogar = conectado("HOGAR", otroHogar);

        long id = solicitudes.crear(hogar, 2, "Cambiar un tomacorriente roto", 7.3772, -72.6473).id();

        assertThat(delHogar.siguiente()).isEqualTo("{\"tipo\":\"solicitud\",\"id\":" + id + "}");
        assertThat(delTecnico.siguiente()).isEqualTo("{\"tipo\":\"abiertas\"}");
        assertThat(deOtroHogar.sinMensajes()).isTrue();
    }

    @Test
    void unaTransaccionRevertidaNoAvisaANadie() throws Exception {
        Cliente delHogar = conectado("HOGAR", hogar);
        Cliente delTecnico = conectado("TECNICO", tecnico);

        transaccion.executeWithoutResult(estado -> {
            solicitudes.crear(hogar, 2, "Cambiar un tomacorriente roto", 7.3772, -72.6473);
            estado.setRollbackOnly();
        });

        assertThat(delHogar.sinMensajes()).isTrue();
        assertThat(delTecnico.sinMensajes()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM solicitudes", Integer.class)).isZero();
    }

    @Test
    void alPostularseAvisaAlHogarDuenio() throws Exception {
        long sol = solicitud(hogar, "E01", T0.plus(Duration.ofMinutes(10)));
        Cliente delHogar = conectado("HOGAR", hogar);
        Cliente deOtroHogar = conectado("HOGAR", otroHogar);

        solicitudes.postular(tecnico, sol, 20_000, "Voy ya");

        assertThat(delHogar.siguiente()).isEqualTo("{\"tipo\":\"solicitud\",\"id\":" + sol + "}");
        assertThat(deOtroHogar.sinMensajes()).isTrue();
    }

    @Test
    void alExpirarUnaVentanaAvisaAlHogar() throws Exception {
        long sol = solicitud(hogar, "E01", T0.minusSeconds(1));
        Cliente delHogar = conectado("HOGAR", hogar);

        assertThat(solicitudes.expirarVencidas()).isEqualTo(1);

        assertThat(delHogar.siguiente()).isEqualTo("{\"tipo\":\"solicitud\",\"id\":" + sol + "}");
    }

    @Test
    void unMensajeDelChatAvisaSoloALasDosPartes() throws Exception {
        long sol = solicitud(hogar, "E03", T0.plus(Duration.ofMinutes(10)));
        jdbc.update("UPDATE solicitudes SET tecnico_id = ?, precio_acordado = 20000, comision_pct = 0.10, comision_cop = 2000 WHERE id = ?",
                tecnico, sol);
        Cliente delHogar = conectado("HOGAR", hogar);
        Cliente delTecnico = conectado("TECNICO", tecnico);
        Cliente deOtroHogar = conectado("HOGAR", otroHogar);

        chat.enviar(hogar, sol, "Ya te abro la puerta");

        String esperado = "{\"tipo\":\"chat\",\"id\":" + sol + "}";
        assertThat(delHogar.siguiente()).isEqualTo(esperado);
        assertThat(delTecnico.siguiente()).isEqualTo(esperado);
        assertThat(deOtroHogar.sinMensajes()).isTrue();
    }

    @Test
    void unTokenInvalidoCierraLaConexion() throws Exception {
        Cliente c = conectar("token-falso");
        for (int i = 0; i < 50 && c.codigoCierre == null; i++) {
            Thread.sleep(100);
        }
        assertThat(c.codigoCierre).isEqualTo(1008);
        assertThat(c.recibidos).isEmpty();
    }

    @Test
    void unMensajeDemasiadoGrandeCierraLaConexion() throws Exception {
        Cliente c = conectado("HOGAR", hogar);
        c.socket.sendText("x".repeat(20_000), true);
        for (int i = 0; i < 50 && c.codigoCierre == null; i++) {
            Thread.sleep(100);
        }
        assertThat(c.codigoCierre).isEqualTo(1009); // 1009 = mensaje demasiado grande
    }
}
