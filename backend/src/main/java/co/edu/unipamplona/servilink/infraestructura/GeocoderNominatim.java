package co.edu.unipamplona.servilink.infraestructura;

import co.edu.unipamplona.servilink.aplicacion.Codigo;
import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import co.edu.unipamplona.servilink.aplicacion.Geocoder;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Es el único punto del sistema que sale a la red hacia Nominatim (OpenStreetMap). Cumple su política de uso:
 * User-Agent que identifica la aplicación, como máximo una petición por segundo (un solo turno por segundo para
 * todo el servidor, sin importar cuántos usuarios busquen), resultados en caché y búsquedas solo cuando el usuario
 * las pide (el frontend no autocompleta mientras se escribe).
 */
@Component
public class GeocoderNominatim implements Geocoder {

    private static final Logger LOG = LoggerFactory.getLogger(GeocoderNominatim.class);

    static final long INTERVALO_MS = 1_000;          // política de Nominatim: máximo 1 petición por segundo
    static final long ESPERA_MAX_MS = 3_000;         // si el turno queda más lejos, se rechaza con RATE_LIMITED
    static final Duration VIGENCIA = Duration.ofHours(24);
    static final Duration VIGENCIA_SIN_RESULTADOS = Duration.ofHours(1);
    static final int MAX_EN_CACHE = 500;
    static final int MAX_RESULTADOS = 5;
    /** Sesgo hacia Pamplona, Cúcuta, Villa del Rosario y Los Patios (izquierda, arriba, derecha, abajo); no excluye el resto del país. */
    static final String CAJA_REGION = "-73.1,8.2,-72.1,7.2";

    private record Entrada(List<Lugar> lugares, Instant vence) {}

    private final RestClient cliente;
    private final String url;
    private final String userAgent;
    private final Clock reloj;
    private final LongConsumer pausa;
    private final Map<String, Entrada> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Entrada> eldest) {
            return size() > MAX_EN_CACHE;
        }
    };
    private long proximoTurnoMs;

    @Autowired
    public GeocoderNominatim(@Value("${servilink.nominatim-url}") String url,
                             @Value("${servilink.nominatim-contacto:}") String contacto, Clock reloj) {
        this(url, contacto, reloj, GeocoderNominatim::dormir);
    }

    GeocoderNominatim(String url, String contacto, Clock reloj, LongConsumer pausa) {
        var fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        this.cliente = RestClient.builder().requestFactory(fabrica).build();
        this.url = url;
        this.userAgent = "ServiLink-Pamplona/1.0 (proyecto academico, Universidad de Pamplona"
                + (contacto == null || contacto.isBlank() ? "" : "; contacto: " + contacto.trim()) + ")";
        this.reloj = reloj;
        this.pausa = pausa;
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    String userAgent() { return userAgent; }

    @Override
    public List<Lugar> buscar(String consulta) {
        String limpia = consulta.trim().replaceAll("\\s+", " "); // lo que se envía: el texto del usuario, sin espacios de más
        String clave = limpia.toLowerCase(Locale.ROOT);            // la caché no distingue mayúsculas
        List<Lugar> guardado = enCache(clave);
        if (guardado != null) {
            return guardado;
        }
        pausa.accept(reservarTurno());
        List<Lugar> lugares = consultarProveedor(limpia);
        synchronized (cache) {
            cache.put(clave, new Entrada(lugares,
                    reloj.instant().plus(lugares.isEmpty() ? VIGENCIA_SIN_RESULTADOS : VIGENCIA)));
        }
        return lugares;
    }

    private List<Lugar> enCache(String clave) {
        synchronized (cache) {
            Entrada e = cache.get(clave);
            if (e == null) {
                return null;
            }
            if (reloj.instant().isAfter(e.vence())) {
                cache.remove(clave);
                return null;
            }
            return e.lugares();
        }
    }

    /** Reserva el siguiente turno de salida; devuelve cuánto esperar. Con la fila muy larga rechaza en vez de acumular. */
    private synchronized long reservarTurno() {
        long ahora = reloj.millis();
        long turno = Math.max(ahora, proximoTurnoMs);
        if (turno - ahora > ESPERA_MAX_MS) {
            throw new ErrorNegocio(Codigo.RATE_LIMITED, "Hay muchas búsquedas en este momento. Intenta de nuevo en unos segundos");
        }
        proximoTurnoMs = turno + INTERVALO_MS;
        return turno - ahora;
    }

    private List<Lugar> consultarProveedor(String consulta) {
        try {
            String cuerpo = cliente.get()
                    .uri(url + "?q={q}&format=json&limit={n}&countrycodes=co&accept-language=es&viewbox={caja}",
                            Map.of("q", consulta, "n", MAX_RESULTADOS, "caja", CAJA_REGION))
                    .header("User-Agent", userAgent)
                    .retrieve().body(String.class); // 4xx y 5xx lanzan excepción
            return interpretar(cuerpo);
        } catch (ErrorNegocio e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.warn("Nominatim no respondió como se esperaba: {}", e.toString());
            throw new ErrorNegocio(Codigo.GEOCODING_UNAVAILABLE,
                    "El buscador de direcciones no está disponible. Toca el mapa o escribe las coordenadas");
        }
    }

    static List<Lugar> interpretar(String cuerpo) {
        List<Object> filas = JsonParserFactory.getJsonParser().parseList(cuerpo == null ? "[]" : cuerpo);
        var lugares = new ArrayList<Lugar>();
        for (Object fila : filas) {
            if (lugares.size() >= MAX_RESULTADOS) {
                break;
            }
            if (!(fila instanceof Map<?, ?> m) || !(m.get("display_name") instanceof String nombre)) {
                continue;
            }
            try {
                double lat = Double.parseDouble(String.valueOf(m.get("lat")));
                double lng = Double.parseDouble(String.valueOf(m.get("lon")));
                if (Double.isFinite(lat) && Double.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180) {
                    lugares.add(new Lugar(nombre, Math.round(lat * 1e6) / 1e6, Math.round(lng * 1e6) / 1e6));
                }
            } catch (NumberFormatException ignorada) {
                // un resultado sin coordenadas válidas se descarta
            }
        }
        return List.copyOf(lugares);
    }
}
