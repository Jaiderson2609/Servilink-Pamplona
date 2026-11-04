package co.edu.unipamplona.servilink;

import static co.edu.unipamplona.servilink.apoyo.ConfiguracionPruebas.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.unipamplona.servilink.apoyo.PruebaIntegracion;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.http.MediaType;

/** El bloqueo de fila solo se comprueba con PostgreSQL real, y se repite porque una carrera es intermitente. */
class ConcurrenciaTest extends PruebaIntegracion {

    int pedir(long solicitudId, long postulacionId, String token, CountDownLatch salida) throws Exception {
        salida.await();
        return mvc.perform(post("/api/v1/solicitudes/" + solicitudId + "/asignacion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(postulacionId)))
                .andReturn().getResponse().getStatus();
    }

    List<Integer> enParalelo(List<long[]> peticiones, String token) throws Exception {
        var salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(peticiones.size());
        List<Future<Integer>> futuros = new ArrayList<>();
        for (long[] p : peticiones) {
            futuros.add(pool.submit(() -> pedir(p[0], p[1], token, salida)));
        }
        salida.countDown();
        List<Integer> codigos = new ArrayList<>();
        for (var f : futuros) {
            codigos.add(f.get());
        }
        pool.shutdown();
        return codigos.stream().sorted().toList();
    }

    @RepeatedTest(10)
    void dosTecnicosDistintosSimultaneosDejanUnGanadorYUn409() throws Exception {
        long hogar = usuario("HOGAR", "Hogar Uno", "3110000001");
        long a = tecnico("Tecnico A", 50_000);
        long b = tecnico("Tecnico B", 50_000);
        long sol = solicitud(hogar, "E02", T0.plus(Duration.ofMinutes(5)));
        long posA = postulacion(sol, a, 20_000);
        long posB = postulacion(sol, b, 20_000);

        var codigos = enParalelo(List.of(new long[] {sol, posA}, new long[] {sol, posB}), token("HOGAR", hogar));

        assertThat(codigos).containsExactly(200, 409); // nunca un 500
        assertThat(jdbc.queryForObject("SELECT count(*) FROM libro_contable", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sum(retenido) FROM billeteras", Long.class)).isEqualTo(2_000L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones_pendientes", Integer.class)).isEqualTo(1);
    }

    @RepeatedTest(10)
    void elMismoTecnicoDosVecesCobraUnaSolaVez() throws Exception {
        long hogar = usuario("HOGAR", "Hogar Uno", "3110000001");
        long a = tecnico("Tecnico A", 50_000);
        long sol = solicitud(hogar, "E02", T0.plus(Duration.ofMinutes(5)));
        long posA = postulacion(sol, a, 20_000);

        var codigos = enParalelo(List.of(new long[] {sol, posA}, new long[] {sol, posA}), token("HOGAR", hogar));

        assertThat(codigos).containsExactly(200, 200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM libro_contable", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT retenido FROM billeteras WHERE tecnico_id = ?", Long.class, a))
                .isEqualTo(2_000L);
    }

    @RepeatedTest(5)
    void unTecnicoEnDosSolicitudesDistintasSoloTomaUna() throws Exception {
        long hogar1 = usuario("HOGAR", "Hogar Uno", "3110000001");
        long hogar2 = usuario("HOGAR", "Hogar Dos", "3110000002");
        long a = tecnico("Tecnico A", 50_000);
        long s1 = solicitud(hogar1, "E02", T0.plus(Duration.ofMinutes(5)));
        long s2 = solicitud(hogar2, "E02", T0.plus(Duration.ofMinutes(5)));
        long p1 = postulacion(s1, a, 20_000);
        long p2 = postulacion(s2, a, 20_000);

        // Cada hogar pide con su propio token.
        var salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        var f1 = pool.submit(() -> pedir(s1, p1, token("HOGAR", hogar1), salida));
        var f2 = pool.submit(() -> pedir(s2, p2, token("HOGAR", hogar2), salida));
        salida.countDown();
        var codigos = List.of(f1.get(), f2.get()).stream().sorted().toList();
        pool.shutdown();

        assertThat(codigos).containsExactly(200, 409);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM solicitudes WHERE tecnico_id = ? AND estado = 'E03'", Integer.class, a))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT retenido FROM billeteras WHERE tecnico_id = ?", Long.class, a))
                .isEqualTo(2_000L);
    }
}
