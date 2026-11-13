package co.edu.unipamplona.servilink.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.unipamplona.servilink.aplicacion.Codigo;
import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import co.edu.unipamplona.servilink.aplicacion.Geocoder.Lugar;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Sin internet ni base de datos: un servidor HTTP falso hace de Nominatim y un reloj controlable mide caché y turnos. */
class GeocoderNominatimTest {

    static final String CONTACTO = "contacto@ejemplo.co";
    static final String DOS_RESULTADOS = """
            [{"display_name":"Carrera 5, Centro, Pamplona, Norte de Santander, Colombia","lat":"7.37723456","lon":"-72.64731234"},
             {"display_name":"Carrera 5, Cúcuta, Norte de Santander, Colombia","lat":"7.8939","lon":"-72.5078"}]""";

    /** Reloj que solo avanza cuando la prueba lo manda. */
    static final class RelojManual extends Clock {
        Instant ahora = Instant.parse("2026-11-13T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zona) { return this; }
        @Override public Instant instant() { return ahora; }
        void avanzar(Duration d) { ahora = ahora.plus(d); }
    }

    HttpServer servidor;
    final AtomicInteger llamadas = new AtomicInteger();
    final AtomicInteger estado = new AtomicInteger(200);
    volatile String cuerpo = DOS_RESULTADOS;
    volatile String ultimaConsulta;
    volatile String ultimoUserAgent;
    final RelojManual reloj = new RelojManual();
    final List<Long> pausas = new CopyOnWriteArrayList<>();

    @BeforeEach
    void levantar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", x -> {
            llamadas.incrementAndGet();
            ultimaConsulta = x.getRequestURI().getRawQuery();
            ultimoUserAgent = x.getRequestHeaders().getFirst("User-Agent");
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            x.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            x.sendResponseHeaders(estado.get(), bytes.length);
            x.getResponseBody().write(bytes);
            x.close();
        });
        servidor.start();
    }

    @AfterEach
    void apagar() { servidor.stop(0); }

    GeocoderNominatim geocoder(String contacto) {
        return new GeocoderNominatim("http://127.0.0.1:" + servidor.getAddress().getPort() + "/search", contacto, reloj, pausas::add);
    }

    GeocoderNominatim geocoder() { return geocoder(CONTACTO); }

    static Codigo codigoDe(Throwable t) { return ((ErrorNegocio) t).codigo(); }

    @Test
    void devuelveLosLugaresConLasCoordenadasRedondeadasA6Decimales() {
        List<Lugar> r = geocoder().buscar("Carrera 5 Pamplona");
        assertThat(r).containsExactly(
                new Lugar("Carrera 5, Centro, Pamplona, Norte de Santander, Colombia", 7.377235, -72.647312),
                new Lugar("Carrera 5, Cúcuta, Norte de Santander, Colombia", 7.8939, -72.5078));
    }

    @Test
    void enviaLaConsultaConLosParametrosYElUserAgentDeLaAplicacion() {
        geocoder().buscar("Carrera 5 #3-20 Pamplona");
        assertThat(ultimaConsulta).contains("q=Carrera%205%20%233-20%20Pamplona").contains("format=json")
                .contains("limit=5").contains("countrycodes=co").contains("accept-language=es").contains("viewbox=");
        assertThat(ultimoUserAgent).startsWith("ServiLink-Pamplona/1.0").contains("Universidad de Pamplona")
                .contains("contacto: " + CONTACTO);
    }

    @Test
    void sinContactoElUserAgentSigueIdentificandoLaAplicacion() {
        var g = geocoder("");
        g.buscar("Pamplona");
        assertThat(ultimoUserAgent).startsWith("ServiLink-Pamplona/1.0").doesNotContain("contacto");
        assertThat(g.userAgent()).isEqualTo(ultimoUserAgent);
    }

    @Test
    void laMismaBusquedaSeResuelveDesdeLaCacheSinMolestarAlProveedor() {
        var g = geocoder();
        var primera = g.buscar("Carrera 5 Pamplona");
        var segunda = g.buscar("  carrera   5  PAMPLONA ");
        assertThat(segunda).isEqualTo(primera);
        assertThat(llamadas.get()).isEqualTo(1);
        assertThat(pausas).hasSize(1); // la segunda ni siquiera pidió turno
    }

    @Test
    void laCacheVenceALas24Horas() {
        var g = geocoder();
        g.buscar("Pamplona");
        reloj.avanzar(Duration.ofHours(23));
        g.buscar("Pamplona");
        assertThat(llamadas.get()).isEqualTo(1);
        reloj.avanzar(Duration.ofHours(2));
        g.buscar("Pamplona");
        assertThat(llamadas.get()).isEqualTo(2);
    }

    @Test
    void unaBusquedaSinResultadosVenceEnUnaHora() {
        cuerpo = "[]";
        var g = geocoder();
        assertThat(g.buscar("sitio que no existe")).isEmpty();
        reloj.avanzar(Duration.ofMinutes(30));
        g.buscar("sitio que no existe");
        assertThat(llamadas.get()).isEqualTo(1);
        reloj.avanzar(Duration.ofMinutes(45));
        g.buscar("sitio que no existe");
        assertThat(llamadas.get()).isEqualTo(2);
    }

    @Test
    void unSoloTurnoPorSegundoParaTodoElServidor() {
        var g = geocoder();
        g.buscar("uno");
        g.buscar("dos");
        g.buscar("tres");
        assertThat(pausas).containsExactly(0L, 1000L, 2000L);
        reloj.avanzar(Duration.ofSeconds(10)); // pasado un rato, el siguiente no espera
        g.buscar("cuatro");
        assertThat(pausas).endsWith(0L);
    }

    @Test
    void conLaFilaMuyLargaRechazaConRateLimitedYNoGastaTurno() {
        var g = geocoder();
        for (int i = 0; i < 4; i++) {
            g.buscar("busqueda " + i); // esperas de 0, 1, 2 y 3 s: es el máximo tolerado
        }
        assertThatThrownBy(() -> g.buscar("una mas")).isInstanceOf(ErrorNegocio.class)
                .satisfies(e -> assertThat(codigoDe(e)).isEqualTo(Codigo.RATE_LIMITED));
        assertThat(llamadas.get()).isEqualTo(4); // la rechazada no llegó a Nominatim
        reloj.avanzar(Duration.ofSeconds(1)); // al pasar el tiempo vuelve a haber turnos
        g.buscar("una mas");
        assertThat(llamadas.get()).isEqualTo(5);
    }

    @Test
    void siElProveedorFallaResponde503YNoGuardaElErrorEnCache() {
        estado.set(500);
        var g = geocoder();
        assertThatThrownBy(() -> g.buscar("Pamplona")).satisfies(e -> assertThat(codigoDe(e)).isEqualTo(Codigo.GEOCODING_UNAVAILABLE));
        estado.set(200);
        assertThat(g.buscar("Pamplona")).hasSize(2); // se recuperó: el fallo anterior no quedó guardado
        assertThat(llamadas.get()).isEqualTo(2);
    }

    @Test
    void siElProveedorLimitaOBloqueaSeTrataComoNoDisponible() {
        for (int codigo : new int[] {403, 429, 503}) {
            estado.set(codigo);
            assertThatThrownBy(() -> geocoder().buscar("consulta " + codigo))
                    .satisfies(e -> assertThat(codigoDe(e)).isEqualTo(Codigo.GEOCODING_UNAVAILABLE));
        }
    }

    @Test
    void unaRespuestaQueNoEsJsonSeTrataComoNoDisponible() {
        cuerpo = "<html>Mantenimiento</html>";
        assertThatThrownBy(() -> geocoder().buscar("Pamplona"))
                .satisfies(e -> assertThat(codigoDe(e)).isEqualTo(Codigo.GEOCODING_UNAVAILABLE));
    }

    @Test
    void conElProveedorCaidoResponde503() {
        var g = geocoder();
        servidor.stop(0);
        assertThatThrownBy(() -> g.buscar("Pamplona"))
                .satisfies(e -> assertThat(codigoDe(e)).isEqualTo(Codigo.GEOCODING_UNAVAILABLE));
    }

    @Test
    void descartaLosResultadosSinCoordenadasValidasYSeQuedaConCincoComoMaximo() {
        var filas = new ArrayList<String>();
        filas.add("{\"display_name\":\"sin latitud\",\"lon\":\"-72.6\"}");
        filas.add("{\"display_name\":\"latitud rota\",\"lat\":\"abc\",\"lon\":\"-72.6\"}");
        filas.add("{\"display_name\":\"fuera de rango\",\"lat\":\"95.0\",\"lon\":\"-72.6\"}");
        filas.add("{\"lat\":\"7.3\",\"lon\":\"-72.6\"}");
        filas.add("\"no es un objeto\"");
        for (int i = 1; i <= 7; i++) {
            filas.add("{\"display_name\":\"lugar " + i + "\",\"lat\":\"7." + i + "\",\"lon\":\"-72." + i + "\"}");
        }
        cuerpo = "[" + String.join(",", filas) + "]";
        var r = geocoder().buscar("lugares");
        assertThat(r).extracting(Lugar::nombre).containsExactly("lugar 1", "lugar 2", "lugar 3", "lugar 4", "lugar 5");
    }
}
