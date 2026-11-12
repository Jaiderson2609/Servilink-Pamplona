package co.edu.unipamplona.servilink.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unipamplona.servilink.config.Configuracion;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Prueba unitaria sin base de datos: el notificador solo debe dar el aviso por enviado si Firebase respondió 2xx.
 * Un error HTTP contado como éxito dejaría la bandeja de salida sin reintentar (mutante M10 de la prueba de mutación).
 */
class NotificadorFcmTest {

    HttpServer servidor;
    final AtomicInteger estadoRespuesta = new AtomicInteger(200);
    final AtomicInteger llamadas = new AtomicInteger();

    @BeforeEach
    void levantarServidorFalso() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            llamadas.incrementAndGet();
            intercambio.getRequestBody().readAllBytes();
            intercambio.sendResponseHeaders(estadoRespuesta.get(), -1);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
    }

    NotificadorFcm notificador() {
        String url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/fcm";
        return new NotificadorFcm(new Configuracion("x".repeat(32), new BigDecimal("0.10"), url,
                false, 1000, 10, 5, 40_000, false, 3600));
    }

    @Test
    void respuestaCorrectaSeCuentaComoEnviada() {
        estadoRespuesta.set(200);
        assertThat(notificador().enviar("token-1", "Te asignaron un servicio")).isTrue();
        assertThat(llamadas.get()).isEqualTo(1);
    }

    @Test
    void errorDelServidorNoSeCuentaComoEnviada() {
        estadoRespuesta.set(500);
        assertThat(notificador().enviar("token-1", "Te asignaron un servicio")).isFalse();
    }

    @Test
    void errorDelClienteNoSeCuentaComoEnviada() {
        estadoRespuesta.set(401);
        assertThat(notificador().enviar("token-1", "Te asignaron un servicio")).isFalse();
    }

    @Test
    void servidorCaidoNoSeCuentaComoEnviada() {
        var notificador = notificador();
        servidor.stop(0);
        assertThat(notificador.enviar("token-1", "Te asignaron un servicio")).isFalse();
    }

    @Test
    void sinTokenNoSeLlamaAFirebaseNiSeReintenta() {
        assertThat(notificador().enviar(null, "texto")).isTrue();
        assertThat(notificador().enviar("  ", "texto")).isTrue();
        assertThat(llamadas.get()).isZero();
    }
}
