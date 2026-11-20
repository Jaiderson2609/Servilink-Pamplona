package co.edu.unipamplona.servilink.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import co.edu.unipamplona.servilink.apoyo.RelojMutable;

/** La cuenta de servicio firma una aserción RS256 válida, la cambia por un token y lo reutiliza hasta que está por vencer. */
class CuentaServicioGoogleTest {

    HttpServer servidor;
    java.security.KeyPair par;
    final AtomicInteger llamadas = new AtomicInteger();
    final CopyOnWriteArrayList<String> cuerpos = new CopyOnWriteArrayList<>();
    final AtomicInteger estado = new AtomicInteger(200);

    @BeforeEach
    void levantar() throws Exception {
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        par = gen.generateKeyPair();
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/token", i -> {
            llamadas.incrementAndGet();
            cuerpos.add(new String(i.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] r = "{\"access_token\":\"token-de-acceso\",\"expires_in\":3600,\"token_type\":\"Bearer\"}".getBytes(StandardCharsets.UTF_8);
            i.getResponseHeaders().add("Content-Type", "application/json");
            i.sendResponseHeaders(estado.get(), estado.get() == 200 ? r.length : -1);
            if (estado.get() == 200) {
                i.getResponseBody().write(r);
            }
            i.close();
        });
        servidor.start();
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
    }

    String uriToken() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/token";
    }

    String credenciales() {
        String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(par.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        return "{\"type\":\"service_account\",\"project_id\":\"proyecto-prueba\",\"client_email\":\"cuenta@proyecto-prueba.iam.gserviceaccount.com\","
                + "\"token_uri\":\"" + uriToken() + "\",\"private_key\":\"" + pem.replace("\n", "\\n") + "\"}";
    }

    @Test
    void sinCredencialesQuedaApagada() {
        assertThat(new CuentaServicioGoogle("").habilitada()).isFalse();
        assertThat(new CuentaServicioGoogle(null).habilitada()).isFalse();
        assertThat(new CuentaServicioGoogle("   ").habilitada()).isFalse();
    }

    @Test
    void credencialesInvalidasLaDejanApagadaSinLanzar() {
        assertThat(new CuentaServicioGoogle("esto no es json").habilitada()).isFalse();
        assertThat(new CuentaServicioGoogle("{\"project_id\":\"p\"}").habilitada()).isFalse();
        assertThat(new CuentaServicioGoogle("{\"project_id\":\"p\",\"client_email\":\"c\",\"private_key\":\"no-es-una-clave\"}").habilitada()).isFalse();
    }

    @Test
    void conCredencialesValidasConoceElProyecto() {
        var cuenta = new CuentaServicioGoogle(credenciales());
        assertThat(cuenta.habilitada()).isTrue();
        assertThat(cuenta.proyectoId()).isEqualTo("proyecto-prueba");
    }

    @Test
    void pideElTokenConUnaAsercionFirmadaConLaClavePrivada() throws Exception {
        var cuenta = new CuentaServicioGoogle(credenciales());
        assertThat(cuenta.tokenAcceso()).isEqualTo("token-de-acceso");

        String cuerpo = cuerpos.get(0);
        assertThat(cuerpo).contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer");
        String asercion = URLDecoder.decode(cuerpo.replaceAll(".*assertion=", ""), StandardCharsets.UTF_8);
        String[] partes = asercion.split("\\.");
        assertThat(partes).hasSize(3);
        var verificador = Signature.getInstance("SHA256withRSA");
        verificador.initVerify((RSAPublicKey) par.getPublic());
        verificador.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verificador.verify(Base64.getUrlDecoder().decode(partes[2]))).isTrue();
        String reclamos = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        assertThat(reclamos).contains("\"iss\":\"cuenta@proyecto-prueba.iam.gserviceaccount.com\"")
                .contains("https://www.googleapis.com/auth/firebase.messaging").contains("\"aud\":\"" + uriToken() + "\"");
    }

    @Test
    void reutilizaElTokenHastaQueFaltenMenosDeCincoMinutos() {
        var reloj = new RelojMutable(Instant.parse("2026-11-20T12:00:00Z"));
        var cuenta = new CuentaServicioGoogle(credenciales(), reloj);
        cuenta.tokenAcceso();
        reloj.fijar(Instant.parse("2026-11-20T12:50:00Z")); // faltan 10 min de 60
        cuenta.tokenAcceso();
        assertThat(llamadas.get()).isEqualTo(1);
        reloj.fijar(Instant.parse("2026-11-20T12:56:00Z")); // faltan 4 min
        cuenta.tokenAcceso();
        assertThat(llamadas.get()).isEqualTo(2);
    }

    @Test
    void siGoogleRechazaLaPeticionLanza() {
        estado.set(401);
        var cuenta = new CuentaServicioGoogle(credenciales());
        assertThatThrownBy(cuenta::tokenAcceso).isInstanceOf(org.springframework.web.client.RestClientException.class);
    }
}
