package co.edu.unipamplona.servilink.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unipamplona.servilink.config.Configuracion;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Con la cuenta de servicio, cada envío a FCM lleva el token de acceso y el proyecto sale de las credenciales. */
class NotificadorFcmAutenticadoTest {

    HttpServer servidor;
    String credenciales;
    final AtomicInteger estadoFcm = new AtomicInteger(200);
    final AtomicInteger llamadasFcm = new AtomicInteger();
    final CopyOnWriteArrayList<String> autorizaciones = new CopyOnWriteArrayList<>();
    final CopyOnWriteArrayList<String> cuerpos = new CopyOnWriteArrayList<>();
    final CopyOnWriteArrayList<String> rutas = new CopyOnWriteArrayList<>();

    @BeforeEach
    void levantar() throws Exception {
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----" + Base64.getEncoder().encodeToString(gen.generateKeyPair().getPrivate().getEncoded())
                + "-----END PRIVATE KEY-----";
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/token", i -> {
            i.getRequestBody().readAllBytes();
            byte[] r = "{\"access_token\":\"acceso-123\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);
            i.getResponseHeaders().add("Content-Type", "application/json");
            i.sendResponseHeaders(200, r.length);
            i.getResponseBody().write(r);
            i.close();
        });
        servidor.createContext("/v1", i -> {
            llamadasFcm.incrementAndGet();
            rutas.add(i.getRequestURI().getPath());
            autorizaciones.add(String.valueOf(i.getRequestHeaders().getFirst("Authorization")));
            cuerpos.add(new String(i.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            i.sendResponseHeaders(estadoFcm.get(), -1);
            i.close();
        });
        servidor.start();
        credenciales = "{\"project_id\":\"mi-proyecto\",\"client_email\":\"c@mi-proyecto.iam.gserviceaccount.com\","
                + "\"token_uri\":\"http://127.0.0.1:" + servidor.getAddress().getPort() + "/token\",\"private_key\":\"" + pem + "\"}";
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
    }

    NotificadorFcm notificador(String json) {
        String url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1/projects/{proyecto}/messages:send";
        var config = new Configuracion("x".repeat(32), new BigDecimal("0.10"), url, false, 1000, 10, 5, 40_000, false, 3600);
        return new NotificadorFcm(config, new CuentaServicioGoogle(json));
    }

    @Test
    void enviaConElTokenDeAccesoYElProyectoDeLasCredenciales() {
        assertThat(notificador(credenciales).enviar("token-del-celular", "Nueva solicitud cerca de ti")).isTrue();
        assertThat(rutas).containsExactly("/v1/projects/mi-proyecto/messages:send");
        assertThat(autorizaciones).containsExactly("Bearer acceso-123");
        assertThat(cuerpos.get(0)).contains("\"token\":\"token-del-celular\"").contains("Nueva solicitud cerca de ti")
                .contains("ServiLink Pamplona").doesNotContain("\"notification\"");
    }

    @Test
    void sinCredencialesNoLlamaANadaYNoReintenta() {
        assertThat(notificador("").enviar("token-del-celular", "texto")).isTrue();
        assertThat(llamadasFcm.get()).isZero();
    }

    @Test
    void unTokenQueYaNoExisteSeDescartaEnVezDeReintentarParaSiempre() {
        estadoFcm.set(404);
        assertThat(notificador(credenciales).enviar("token-viejo", "texto")).isTrue();
    }

    @Test
    void otrosErroresSiSeReintentan() {
        estadoFcm.set(503);
        assertThat(notificador(credenciales).enviar("token", "texto")).isFalse();
        estadoFcm.set(403);
        assertThat(notificador(credenciales).enviar("token", "texto")).isFalse();
    }

    @Test
    void siGoogleNoDaElTokenDeAccesoElAvisoQuedaPendiente() {
        servidor.removeContext("/token");
        assertThat(notificador(credenciales).enviar("token", "texto")).isFalse();
        assertThat(llamadasFcm.get()).isZero();
    }
}
