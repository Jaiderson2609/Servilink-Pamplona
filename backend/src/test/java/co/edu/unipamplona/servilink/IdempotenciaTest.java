package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DT-10 y CP-09: un reintento con la misma Idempotency-Key no repite la operación (DDS 9 y 12.1). */
class IdempotenciaTest extends PruebaApi {

    static final String CLAVE = "clave-prueba-0001";
    String hogar, tecnico;

    @BeforeEach
    void escenario() throws Exception {
        hogar = cuenta("HOGAR", "hogar@correo.co");
        tecnico = cuenta("TECNICO", "tec@correo.co");
        recargar(tecnico, 50_000);
    }

    long solicitudes() {
        return jdbc.queryForObject("SELECT count(*) FROM solicitudes", Long.class);
    }

    @Test
    void crearDosVecesConLaMismaClaveDevuelveLaMismaSolicitud() throws Exception {
        var a = enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto"), CLAVE)
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotent-Replay"));
        long id = ((Number) campo(a, "$.id")).longValue();
        // Sin clave, la segunda llamada sería ACTIVE_REQUEST_EXISTS; con clave se repite la respuesta guardada.
        var b = enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto"), CLAVE)
                .andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replay", "true"));
        assertThat(((Number) campo(b, "$.id")).longValue()).isEqualTo(id);
        assertThat(solicitudes()).isEqualTo(1);
    }

    @Test
    void sinLaCabeceraNoCambiaNada() throws Exception {
        enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto")).andExpect(status().isCreated());
        enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_REQUEST_EXISTS"));
    }

    @Test
    void elDobleToqueAlElegirTecnicoRetieneLaComisionUnaSolaVez() throws Exception {
        long sol = ((Number) campo(enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto")), "$.id")).longValue();
        long pos = ((Number) campo(enviar(tecnico, "/api/v1/service-requests/" + sol + "/applications", "{\"precioCop\":20000}", "post-0000001"), "$.id")).longValue();
        enviar(hogar, "/api/v1/applications/" + pos + "/select", null, CLAVE).andExpect(status().isOk());
        enviar(hogar, "/api/v1/applications/" + pos + "/select", null, CLAVE).andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM libro_contable WHERE tipo = 'RETENCION'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones_pendientes", Long.class)).isEqualTo(1);
    }

    @Test
    void laRecargaConClaveSeAcreditaUnaSolaVez() throws Exception {
        enviar(tecnico, "/api/v1/wallet/topups/demo", "{\"valorCop\":10000}", CLAVE).andExpect(status().isOk());
        enviar(tecnico, "/api/v1/wallet/topups/demo", "{\"valorCop\":10000}", CLAVE).andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"));
        pedir(tecnico, "/api/v1/wallet").andExpect(jsonPath("$.saldoDisponibleCop").value(60_000));
    }

    @Test
    void unaClaveNoSirveParaOtraOperacion() throws Exception {
        enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto"), CLAVE).andExpect(status().isCreated());
        enviar(tecnico, "/api/v1/wallet/topups/demo", "{\"valorCop\":10000}", CLAVE).andExpect(status().isOk()); // otro usuario: la misma clave es independiente
        enviar(tecnico, "/api/v1/service-requests/1/applications", "{\"precioCop\":20000}", CLAVE).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unaRespuestaDeErrorNoSeGuarda() throws Exception {
        enviar(hogar, "/api/v1/service-requests", "{\"oficioId\":2}", CLAVE).andExpect(status().isBadRequest());
        enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto"), CLAVE).andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replay"));
    }

    @Test
    void laFormaDeLaClaveSeValida() throws Exception {
        enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto"), "corta").andExpect(status().isBadRequest());
        enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto"), "tiene espacios y ñ!!").andExpect(status().isBadRequest());
        assertThat(solicitudes()).isZero();
    }

    @Test
    void unaClaveConMasDeVeinticuatroHorasSeVuelveAEjecutar() throws Exception {
        enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto"), CLAVE).andExpect(status().isCreated());
        jdbc.update("UPDATE claves_idempotencia SET creada_en = ?", java.sql.Timestamp.from(reloj.instant().minus(Duration.ofHours(25))));
        enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto"), CLAVE).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_REQUEST_EXISTS")); // se ejecutó de nuevo
    }

    @Test
    void laLimpiezaBorraLasClavesVencidas() throws Exception {
        enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto"), CLAVE).andExpect(status().isCreated());
        enviar(tecnico, "/api/v1/wallet/topups/demo", "{\"valorCop\":10000}", "otra-clave-0002").andExpect(status().isOk());
        jdbc.update("UPDATE claves_idempotencia SET creada_en = ? WHERE clave = ?",
                java.sql.Timestamp.from(reloj.instant().minus(Duration.ofHours(25))), CLAVE);
        assertThat(limpieza.limpiar()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM claves_idempotencia", Long.class)).isEqualTo(1);
    }

    @org.springframework.beans.factory.annotation.Autowired co.edu.unipamplona.servilink.aplicacion.LimpiezaIdempotencia limpieza;

    @Test
    void dosPeticionesSimultaneasConLaMismaClaveCreanUnaSolaSolicitud() throws Exception {
        var salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<long[]>> futuros = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futuros.add(pool.submit(() -> {
                salida.await();
                var r = enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto"), CLAVE).andReturn().getResponse();
                return new long[] {r.getStatus(), r.getStatus() == 201 ? ((Number) com.jayway.jsonpath.JsonPath.read(r.getContentAsString(), "$.id")).longValue() : -1};
            }));
        }
        salida.countDown();
        long[] a = futuros.get(0).get(), b = futuros.get(1).get();
        pool.shutdown();
        assertThat(a[0]).isEqualTo(201);
        assertThat(b[0]).isEqualTo(201);
        assertThat(a[1]).isEqualTo(b[1]);
        assertThat(solicitudes()).isEqualTo(1);
    }
}
