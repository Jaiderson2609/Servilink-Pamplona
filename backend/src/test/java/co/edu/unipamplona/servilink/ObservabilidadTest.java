package co.edu.unipamplona.servilink;

import static co.edu.unipamplona.servilink.apoyo.ConfiguracionPruebas.T0;
import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unipamplona.servilink.apoyo.PruebaIntegracion;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** FPI-29: el endpoint crítico (la asignación) deja métrica, traza y registro JSON, sin datos personales. */
// Servidor real: los endpoints de actuator no se sirven a través de MockMvc.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class ObservabilidadTest extends PruebaIntegracion {

    static final String NOMBRE_TECNICO = "Tecnico Privado Demo";
    static final String TELEFONO_TECNICO = "3229998877";
    static final String NOMBRE_HOGAR = "Hogar Privado Demo";
    static final String TELEFONO_HOGAR = "3118887766";

    @LocalServerPort int puerto;
    final HttpClient http = HttpClient.newHttpClient();
    long hogar, tecnico, sol, pos;

    @BeforeEach
    void escenario() {
        hogar = usuario("HOGAR", NOMBRE_HOGAR, TELEFONO_HOGAR);
        tecnico = tecnico(NOMBRE_TECNICO, 50_000);
        sol = solicitud(hogar, "E02", T0.plus(Duration.ofMinutes(5)));
        pos = postulacion(sol, tecnico, 20_000);
    }

    HttpResponse<String> llamar(String metodo, String ruta, String autorizacion) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, HttpRequest.BodyPublishers.noBody());
        if (autorizacion != null) {
            req.header("Authorization", autorizacion);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    void asignar(String token, long postulacion) throws Exception {
        llamar("PATCH", "/api/v1/applications/" + postulacion + "/select", "Bearer " + token);
    }

    String metricas() throws Exception {
        String basica = "Basic " + Base64.getEncoder().encodeToString("prometheus:clave-de-metricas-de-pruebas".getBytes());
        var r = llamar("GET", "/actuator/prometheus", basica);
        assertThat(r.statusCode()).isEqualTo(200);
        return r.body();
    }

    @Test
    void laAsignacionDejaMetricaConResultadoYElTraficoHttp() throws Exception {
        asignar(token("HOGAR", hogar), pos);
        String m = metricas();
        assertThat(m).contains("servilink_asignacion_seconds_count")
                .contains("resultado=\"ASIGNADA\"")
                .contains("servilink_asignacion_seconds_bucket") // histograma: permite calcular el p95
                .contains("http_server_requests_seconds_bucket")
                .contains("uri=\"/api/v1/applications/{applicationId}/select\"");
    }

    @Test
    void unRechazoDeNegocioSeCuentaPorSuCodigoYNoComoErrorDelSistema() throws Exception {
        long sinSaldo = tecnico("Sin Saldo", 100);
        long otroHogar = usuario("HOGAR", "Otro Hogar", "3110000009");
        long solB = solicitud(otroHogar, "E02", T0.plus(Duration.ofMinutes(5)));
        long posB = postulacion(solB, sinSaldo, 20_000);
        asignar(token("HOGAR", otroHogar), posB);
        assertThat(metricas()).contains("resultado=\"INSUFFICIENT_BALANCE\"");
    }

    @Test
    void elRegistroEsJsonTieneTrazaYNoContieneDatosPersonales(CapturedOutput salida) throws Exception {
        String jwt = token("HOGAR", hogar);
        asignar(jwt, pos);
        String linea = salida.getOut().lines().filter(l -> l.contains("Asignación procesada")).findFirst().orElseThrow();
        assertThat(linea).startsWith("{").contains("\"evento\":\"asignacion\"").contains("\"resultado\":\"ASIGNADA\"")
                .contains("\"solicitudId\":" + sol).contains("trace");
        assertThat(salida.getAll())
                .doesNotContain(NOMBRE_TECNICO).doesNotContain(TELEFONO_TECNICO)
                .doesNotContain(NOMBRE_HOGAR).doesNotContain(TELEFONO_HOGAR)
                .doesNotContain(jwt).doesNotContain("Bearer");
    }

    @Test
    void lasMetricasNoSonPublicasNiAceptanUnaClaveIncorrecta() throws Exception {
        assertThat(llamar("GET", "/actuator/prometheus", null).statusCode()).isEqualTo(401);
        String mala = "Basic " + Base64.getEncoder().encodeToString("prometheus:otra-clave-cualquiera".getBytes());
        assertThat(llamar("GET", "/actuator/prometheus", mala).statusCode()).isEqualTo(401);
        // Un JWT de usuario tampoco abre las métricas.
        assertThat(llamar("GET", "/actuator/prometheus", "Bearer " + token("HOGAR", hogar)).statusCode()).isEqualTo(401);
    }
}
